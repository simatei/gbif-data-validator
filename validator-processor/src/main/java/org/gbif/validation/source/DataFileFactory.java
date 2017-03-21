package org.gbif.validation.source;

import org.gbif.dwc.terms.Term;
import org.gbif.dwca.io.Archive;
import org.gbif.dwca.io.ArchiveFactory;
import org.gbif.dwca.io.ArchiveFile;
import org.gbif.dwca.io.UnsupportedArchiveException;
import org.gbif.utils.file.csv.UnkownDelimitersException;
import org.gbif.validation.api.DataFile;
import org.gbif.validation.api.DwcDataFile;
import org.gbif.validation.api.TabularDataFile;
import org.gbif.validation.api.TermIndex;
import org.gbif.validation.api.model.DwcFileType;
import org.gbif.validation.api.model.FileFormat;
import org.gbif.validation.util.FileNormalizer;
import org.gbif.ws.util.ExtraMediaTypes;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Main factory used to create and prepare {@link DataFile} and {@link TabularDataFile}.
 * Those objects are not simple to create so we centralize this process here.
 */
public class DataFileFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DataFileFactory.class);

  private static final String CSV_EXT = ".csv";

  /**
   * Recognized sheet names in Excel workbook when the workbook contains more than 1 sheet.
   * Mostly name of sheets used in GBIF provided templates.
   */
  private static final List<String> KNOWN_EXCEL_SHEET_NAMES = Collections.unmodifiableList(
          Arrays.asList("sampling events", "classification", "occurrences"));

  /**
   * Function to select an Excel sheet within multiple sheets.
   * This will select the first known sheet starting from the left.
   */
  private static final Function<List<String>, Optional<String>> SELECT_EXCEL_SHEET =
          sheetsList -> sheetsList.stream()
                  .filter(name -> KNOWN_EXCEL_SHEET_NAMES.contains(name.toLowerCase()))
                  .findFirst();

  /**
   * Private constructor.
   */
  private DataFileFactory() {
    //empty method
  }

  /**
   * Creates a new {@link DataFile} with the minimum information required.
   *
   * @param filePath
   * @param sourceFileName
   * @param fileFormat
   * @param contentType
   * @return
   */
  public static DataFile newDataFile(Path filePath, String sourceFileName, FileFormat fileFormat,
                                        String contentType) {
    return new DataFile(filePath, sourceFileName, fileFormat, contentType);
  }

  /**
   *
   * @param tabDatafile
   * @param splitFilePath
   * @param lineOffset
   * @param numberOfLines
   * @param withHeader
   * @return
   */
  public static TabularDataFile newTabularDataFileSplit(TabularDataFile tabDatafile, Path splitFilePath,
                                                        Integer lineOffset, Integer numberOfLines, boolean withHeader) {
    return new TabularDataFile(splitFilePath,
            tabDatafile.getSourceFileName(),
            tabDatafile.getRowType(), tabDatafile.getType(), tabDatafile.getColumns(),
            tabDatafile.getRecordIdentifier().orElse(null), tabDatafile.getDefaultValues().orElse(null),
            lineOffset, withHeader, tabDatafile.getCharacterEncoding(), tabDatafile.getDelimiterChar(),
            tabDatafile.getQuoteChar(), numberOfLines);
  }

  /**
   * Prepare the {@link DataFile} for evaluation.
   * This step includes reading the headers from the file and generating a list of {@link TabularDataFile}.
   *
   * @param dataFile
   * @param destinationFolder Preparing {@link DataFile} includes rewriting them in a normalized format. This {@link Path}
   *                          represents the destination of normalized files.
   * @return unvalidated {@link DwcDataFile}
   * @throws IOException
   * @throws UnsupportedDataFileException
   */
  public static DwcDataFile prepareDataFile(DataFile dataFile, Path destinationFolder) throws IOException,
          UnsupportedDataFileException {
    Objects.requireNonNull(dataFile.getFilePath(), "filePath shall be provided");
    Objects.requireNonNull(dataFile.getFileFormat(), "fileFormat shall be provided");
    Objects.requireNonNull(destinationFolder, "destinationFolder shall be provided");
    Preconditions.checkState(Files.isDirectory(destinationFolder), "destinationFolder should point to a folder");

    DataFilePreview dataFilePreview = DataFilePreview.extractFrom(dataFile);
    List<TabularDataFile> dataFileList = normalizeAndPrepare(dataFile, destinationFolder, dataFilePreview);

    Map<DwcFileType, List<TabularDataFile>> dfPerDwcFileType = dataFileList.stream()
            .collect(Collectors.groupingBy(TabularDataFile::getType));
    List<TabularDataFile> coreTabularDataFile =
            dfPerDwcFileType.getOrDefault(DwcFileType.CORE, new ArrayList<>());

    // Not really possible but just in case
    if(coreTabularDataFile.size() > 1) {
      LOG.warn("DataFile should have exactly 1 core. {}", dataFile);
      throw new UnsupportedDataFileException("DataFile should have exactly 1 core. Found " + coreTabularDataFile.size());
    }

    Optional<TabularDataFile> coreDf = coreTabularDataFile.stream().findFirst();
    return new DwcDataFile(dataFile, coreDf.orElse(null),
            dfPerDwcFileType.get(DwcFileType.EXTENSION),
            dataFilePreview.getMetadataFilePath().orElse(null));
  }

  /**
   * Responsible to decide and run file normalization and prepare the {@link DataFile} from the result.
   *
   * @return list of prepared {@link TabularDataFile}
   */
  private static List<TabularDataFile> normalizeAndPrepare(DataFile dataFile, Path destinationFolder,
                                                           DataFilePreview dataFilePreview)
          throws IOException, UnsupportedDataFileException {

    //Spreadsheet is a special case since the crawling will not take it at the moment
    if (FileFormat.SPREADSHEET == dataFile.getFileFormat()) {
      SpreadsheetConversionResult conversionResult = handleSpreadsheetConversion(dataFile, destinationFolder);
      Map<Path, Integer> pathAndLines = new HashMap<>();
      pathAndLines.put(conversionResult.getResultPath().getFileName(), conversionResult.getNumOfLines());
      return prepareDwcBased(conversionResult.getResultPath(), dataFile, pathAndLines);
    } else {
      Map<Path, Integer> normalizedFiles = FileNormalizer.normalizeTarget(dataFile.getFilePath(),
              destinationFolder, dataFilePreview.getCharsetsByPath());
      return prepareDwcBased(destinationFolder, dataFile, normalizedFiles);
    }
  }

  /**
   * Given a {@link DataFile} pointing to folder containing the extracted DarwinCore archive or a single file,
   * this method creates a list of {@link TabularDataFile} for each of the data component (core + extensions).
   *
   * @param pathToOpen       folder of an extracted DarwinCore Archive or a single tabular file
   * @param originalDataFile
   * @param pathAndLines     mapping between all {@link Path} and their number of lines
   *
   * @return
   */
  private static List<TabularDataFile> prepareDwcBased(Path pathToOpen, DataFile originalDataFile,
                                                       Map<Path, Integer> pathAndLines)
          throws IOException, UnsupportedDataFileException {

    List<TabularDataFile> dataFileList = new ArrayList<>();
    try {
      Archive archive = ArchiveFactory.openArchive(pathToOpen.toFile());

      //add the core first, if there is no core the exception must be handled by the caller
      ArchiveFile core = archive.getCore();
      if (core != null) {
        dataFileList.add(createDwcBasedTabularDataFile(core,
                determineSourceFilename(originalDataFile, core),
                DwcFileType.CORE, pathAndLines.get(Paths.get(safeGetCoreLocation(archive)))));
      }
      for (ArchiveFile ext : archive.getExtensions()) {
        dataFileList.add(createDwcBasedTabularDataFile(ext,
                ext.getLocationFile().getName(), DwcFileType.EXTENSION,
                pathAndLines.get(Paths.get(ext.getLocation()))));
      }
    } catch (UnkownDelimitersException | UnsupportedArchiveException ex) {
      //re-throw the exception as UnsupportedDataFileException
      throw new UnsupportedDataFileException(ex.getMessage());
    }
    return dataFileList;
  }

  /**
   * Creates a new {@link TabularDataFile} for a DarwinCore rowType as {@link ArchiveFile}.
   *
   * @param archiveFile
   * @param sourceFileName
   * @param type
   * @param numberOfLines
   * @return
   * @throws IOException
   */
  private static TabularDataFile createDwcBasedTabularDataFile(ArchiveFile archiveFile,
                                                               String sourceFileName,
                                                               DwcFileType type,
                                                               int numberOfLines) throws IOException {
    Term[] headers = archiveFile.getHeader();
    Map<Term, String> defaultValues = archiveFile.getDefaultValues().orElse(null);
    TermIndex recordIdentifier = archiveFile.getId() == null ? null :
            new TermIndex(archiveFile.getId().getIndex(), archiveFile.getId().getTerm());

    //if TermIndex is null we need to report an error

    return new TabularDataFile(archiveFile.getLocationFile().toPath(),
            sourceFileName,
            archiveFile.getRowType(), type, headers, recordIdentifier, defaultValues,
            null, //no line offset
            archiveFile.getIgnoreHeaderLines()!= null
            && archiveFile.getIgnoreHeaderLines() > 0,
            Charset.forName(archiveFile.getEncoding()),
            archiveFile.getFieldsTerminatedBy().charAt(0),
            archiveFile.getFieldsEnclosedBy(), numberOfLines);
  }

  /**
   * Perform conversion a spreadsheets based on {@code spreadsheetDataFile.getContentType()}.
   *
   * @param spreadsheetDataFile {@link DataFile} that requires conversion.
   * @param destinationFolder destination folder where the output will be stored in the form a random UUID with
   *                          CSV_EXT.
   *
   * @return {@link SpreadsheetConversionResult} containing the result of the conversion
   *
   * @throws IOException
   * @throws UnsupportedDataFileException if conversion can not be applied or it returned no content
   */
  private static SpreadsheetConversionResult handleSpreadsheetConversion(DataFile spreadsheetDataFile, Path destinationFolder)
          throws IOException, UnsupportedDataFileException {

    Preconditions.checkState(FileFormat.SPREADSHEET == spreadsheetDataFile.getFileFormat(),
            "prepareSpreadsheet only handles FileFormat.SPREADSHEET");

    Path conversionFolder = destinationFolder.resolve(UUID.randomUUID().toString());
    Files.createDirectory(conversionFolder);

    Path spreadsheetFile = spreadsheetDataFile.getFilePath();
    String contentType = spreadsheetDataFile.getContentType();

    Path csvFile = destinationFolder.resolve(UUID.randomUUID() + CSV_EXT);
    SpreadsheetConversionResult conversionResult;
    if (ExtraMediaTypes.APPLICATION_OFFICE_SPREADSHEET.equalsIgnoreCase(contentType) ||
            ExtraMediaTypes.APPLICATION_EXCEL.equalsIgnoreCase(contentType)) {
      conversionResult = SpreadsheetConverters.convertExcelToCSV(spreadsheetFile, csvFile, SELECT_EXCEL_SHEET);
    } else if (ExtraMediaTypes.APPLICATION_OPEN_DOC_SPREADSHEET.equalsIgnoreCase(contentType)) {
      conversionResult = SpreadsheetConverters.convertOdsToCSV(spreadsheetFile, csvFile);
    } else {
      LOG.warn("Unhandled contentType {}", contentType);
      throw new UnsupportedDataFileException(contentType + " can not be converted");
    }

    //If no lines were converted
    if (conversionResult.getNumOfLines() <= 0) {
      LOG.warn("No line written while converting {}", spreadsheetDataFile);
      throw new UnsupportedDataFileException(contentType + " conversion returned no content (no line)");
    }

    return conversionResult;
  }

  /**
   * When used on a single file the location comes from the Archive object. This method simply makes it
   * easier to get.
   *
   * @param archive
   *
   * @return
   */
  private static String safeGetCoreLocation(Archive archive) {
    if (archive.getCore() == null || archive.getCore().getLocation() == null) {
      return archive.getLocation().getName();
    }
    return archive.getCore().getLocation();
  }

  /**
   * This method allows to use the name of the {@link ArchiveFile} instead of the {@link DataFile} when used
   * the {@link FileFormat} is {@link FileFormat#DWCA}.
   *
   * @param originalDataFile
   * @param core
   *
   * @return
   */
  private static String determineSourceFilename(DataFile originalDataFile, ArchiveFile core) {
    if (FileFormat.DWCA == originalDataFile.getFileFormat() && core.getLocationFile() != null) {
      return core.getLocationFile().getName();
    }

    return originalDataFile.getSourceFileName();
  }

  /**
   * Internal class used to store basic data about a {@link DataFile} before we apply transformations.
   */
  private static class DataFilePreview {

    private final Map<Path, Charset> charsetsByPath;
    private final Path metadataFilePath;

    private DataFilePreview(Path metadataFilePath, Map<Path, Charset> charsetsByPath) {
      this.metadataFilePath = metadataFilePath;
      this.charsetsByPath = charsetsByPath;
    }

    /**
     * If possible, tries to extract {@link DataFilePreview} from the provided {@link DataFile}.
     * @param dataFile
     * @return
     * @throws IOException
     * @throws UnsupportedCharsetException
     */
    static DataFilePreview extractFrom(DataFile dataFile) throws IOException, UnsupportedCharsetException {
      Map<Path, Charset> charsetsByPath = new HashMap<>();
      Path metadataFilePath = null;

      if(dataFile.getFileFormat().isTabularBased()) {
        try {
          Archive archive = ArchiveFactory.openArchive(dataFile.getFilePath().toFile());
          if (archive.getMetadataLocationFile() != null) {
            metadataFilePath = archive.getMetadataLocationFile().toPath();
          }

          ArchiveFile core = archive.getCore();
          if (core != null) {
            extractCharset(core.getEncoding()).ifPresent(cs -> charsetsByPath.put(Paths.get(safeGetCoreLocation(archive)), cs));
          }
          for (ArchiveFile ext : archive.getExtensions()) {
            extractCharset(ext.getEncoding()).ifPresent(cs -> charsetsByPath.put(Paths.get(safeGetCoreLocation(archive)), cs));
          }
        } catch (UnkownDelimitersException ignore) {
          //ignore, it is not the goal of this function
        }
      }

      return new DataFilePreview(metadataFilePath, charsetsByPath);
    }

    /**
     * Tries to parse the charsetName and return Optional.empty if null or empty.
     * @param charsetName
     * @return
     * @throws UnsupportedCharsetException
     */
    private static Optional<Charset> extractCharset(String charsetName) throws UnsupportedCharsetException {
      return StringUtils.isBlank(charsetName) ? Optional.empty() : Optional.of(Charset.forName(charsetName));
    }

    public Map<Path, Charset> getCharsetsByPath() {
      return charsetsByPath;
    }

    public Optional<Path> getMetadataFilePath() {
      return Optional.ofNullable(metadataFilePath);
    }
  }

}
