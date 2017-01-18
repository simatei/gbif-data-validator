package org.gbif.validation.evaluator;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Safeguard tests to make sure OCCURRENCE_ISSUE_MAPPING can be loaded.
 *
 */
public class OccurrenceIssueEvaluationTypeMappingTest {

  @Test
  public void testOccurrenceIssueEvaluationTypeMapping() {
    //we just test that we can load the map since it is initialized in a static block
    assertFalse(InterpretationRemarkEvaluationTypeMapping.OCCURRENCE_ISSUE_MAPPING.isEmpty());
  }

}
