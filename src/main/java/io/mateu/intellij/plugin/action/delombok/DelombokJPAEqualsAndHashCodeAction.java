package io.mateu.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.action.delombok.AbstractDelombokAction;
import de.plushnikov.intellij.plugin.action.delombok.DelombokHandler;
import de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor;

public class DelombokJPAEqualsAndHashCodeAction extends AbstractDelombokAction {

  @Override
  protected DelombokHandler createHandler() {
    return new DelombokHandler(ServiceManager.getService(EqualsAndHashCodeProcessor.class));
  }
}
