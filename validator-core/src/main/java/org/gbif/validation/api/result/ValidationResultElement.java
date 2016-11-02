package org.gbif.validation.api.result;

import java.util.List;

/**
 * A {@link ValidationResultElement} represents an element in the resource.
 * For DarwinCore Archive this could be the meta.xml, a core file or an extension file.
 *
 */
public interface ValidationResultElement {
  List<ValidationIssue> getIssues();
}