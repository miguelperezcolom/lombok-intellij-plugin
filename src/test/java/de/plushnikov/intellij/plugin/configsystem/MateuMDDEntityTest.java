package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class MateuMDDEntityTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/mateu";
  }

  public void testVersionFieldName$MateuMDDEntityTest() throws IOException {
    doTest();
  }

}
