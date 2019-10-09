package io.mateu.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.action.lombok.BaseLombokAction;

public class LombokMateuMDDEntityAction extends BaseLombokAction {

  public LombokMateuMDDEntityAction() {
    super(new LombokMateuMDDEntityHandler());
  }

}
