package org.gbif.validation.jobserver;

import org.gbif.validation.api.model.JobStatusResponse;

import java.util.Optional;

/**
 * Interface to decouple how the job result information  is stored.
 */
public interface JobStorage {

  /**
   * Gets the data stored of jobId.
   */
  Optional<JobStatusResponse<?>> get(long jobId);

  /**
   * Stores/overwrites the data of a jobId.
   */
  void put(JobStatusResponse<?> data);

}