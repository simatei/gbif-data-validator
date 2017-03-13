package org.gbif.validation.api;

import org.gbif.dwc.terms.Term;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Basically grouping all the components of DarwinCore based data.
 * It represents a DarwinCore file in the sense that data is related to Darwin Core terms.
 */
public class DwcDataFile {

  private final DataFile dataFile;

  //currently organized as "star schema"
  private final TabularDataFile core;
  private final List<TabularDataFile> extensions;
  private final Map<Term, TabularDataFile> tabularDataFileByTerm;
  private final Path metadataFilePath;

  public DwcDataFile(DataFile dataFile, TabularDataFile core, List<TabularDataFile> extensions,
                     Path metadataFilePath) {
    this.dataFile = dataFile;
    this.core = core;
    this.extensions = extensions;
    this.metadataFilePath = metadataFilePath;

    Map<Term, TabularDataFile> fileByRowType = new HashMap<>();
    fileByRowType.put(core.getRowType(), core);

    getExtensions().ifPresent(ext -> ext.stream()
            .forEach(e -> fileByRowType.put(e.getRowType(), e)));

    tabularDataFileByTerm = Collections.unmodifiableMap(fileByRowType);
  }

  public DataFile getDataFile() {
    return dataFile;
  }

  public List<TabularDataFile> getTabularDataFiles() {
    return new ArrayList<>(tabularDataFileByTerm.values());
  }

  /**
   *
   * @param term
   * @return matching {@link TabularDataFile} or null
   */
  public TabularDataFile getByRowType(Term term) {
    return tabularDataFileByTerm.get(term);
  }

  public TabularDataFile getCore() {
    return core;
  }

  public Optional<List<TabularDataFile>> getExtensions() {
    return  Optional.ofNullable(extensions);
  }

  public Optional<Path> getMetadataFilePath() {
    return Optional.ofNullable(metadataFilePath);
  }
}