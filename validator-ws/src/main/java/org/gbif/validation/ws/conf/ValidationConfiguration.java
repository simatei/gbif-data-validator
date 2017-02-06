package org.gbif.validation.ws.conf;

import java.net.URL;

/**
 * Configuration settings required by the data validation web services.
 */
public class ValidationConfiguration {

  //name of the parameter used when POSTing a file
  public static final String FILE_POST_PARAM_NAME = "file";

  /**
   * Url to the GBIF Rest API.
   */
  private String apiUrl;

  private URL extensionDiscoveryUrl;

  /**
   * Maximum number of lines a file can contains until we split it.
   */
  private Integer fileSplitSize;

  /**
   * Directory used to copy data files to be validated.
   */
  private String workingDir;

  /**
   * Path to the data validation public path.
   */
  private String apiDataValidationPath;

  /**
   * Directory where FileJobStorage stores job results.
   */
  private String jobResultStorageDir;

  public String getApiUrl() {
    return apiUrl;
  }

  public void setApiUrl(String apiUrl) {
    this.apiUrl = apiUrl;
  }

  public String getApiDataValidationPath() {
    return apiDataValidationPath;
  }

  public void setExtensionDiscoveryUrl(URL extensionDiscoveryUrl){
    this.extensionDiscoveryUrl = extensionDiscoveryUrl;
  }

  public URL getExtensionDiscoveryUrl() {
    return extensionDiscoveryUrl;
  }

  public void setApiDataValidationPath(String apiDataValidationPath) {
    this.apiDataValidationPath = apiDataValidationPath;
  }

  public Integer getFileSplitSize() {
    return fileSplitSize;
  }

  public void setFileSplitSize(Integer fileSplitSize) {
    this.fileSplitSize = fileSplitSize;
  }

  public String getWorkingDir() {
    return workingDir;
  }

  public void setWorkingDir(String workingDir) {
    this.workingDir = workingDir;
  }

  public String getJobResultStorageDir() {
    return jobResultStorageDir;
  }

  public void setJobResultStorageDir(String jobResultStorageDir) {
    this.jobResultStorageDir = jobResultStorageDir;
  }
}
