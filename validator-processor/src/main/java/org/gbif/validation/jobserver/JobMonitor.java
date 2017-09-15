package org.gbif.validation.jobserver;

import org.gbif.validation.api.model.JobDataOutput;
import org.gbif.validation.api.model.JobStatusResponse;
import org.gbif.validation.jobserver.messages.DataJob;

import java.util.function.Supplier;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import static akka.japi.pf.ReceiveBuilder.match;

/**
 * Actor that acts as the main controller to coordinate the creation of jobs and store results.
 * C.G. I would rename this class JobCoordinatorActor
 */
public class JobMonitor extends AbstractLoggingActor {

  /**
   * Creates an MockActor instance that will wait 'waitBeforeDie' before die.
   */
  public JobMonitor(Supplier<Props> propsSupplier, JobStorage jobStorage) {
    receive(
        match(DataJob.class, dataJob -> {
          //creates a actor that is responsible to handle a this jobData
          ActorRef jobMaster = getContext().actorOf(propsSupplier.get(),
                                                     String.valueOf(dataJob.getJobId())); //the jobId used as Actor's name
          jobMaster.tell(dataJob, self());
        }).
        match(JobStatusResponse.class, jobStorage::put).
        match(JobDataOutput.class, jobStorage::put)
        .matchAny(this::unhandled)
        .build()
    );
  }

}
