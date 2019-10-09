package io.mateu.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.action.lombok.BaseLombokAction;

public class LombokJPAEqualsAndHashcodeAction extends BaseLombokAction {

  public LombokJPAEqualsAndHashcodeAction() {
    super(new LombokJPAEqualsAndHashcodeHandler());
  }

}
