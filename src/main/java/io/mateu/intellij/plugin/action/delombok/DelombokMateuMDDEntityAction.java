package io.mateu.intellij.plugin.action.delombok;

import com.intellij.openapi.components.ServiceManager;
import de.plushnikov.intellij.plugin.action.delombok.AbstractDelombokAction;
import de.plushnikov.intellij.plugin.action.delombok.DelombokHandler;
import io.mateu.intellij.plugin.processor.clazz.MateuMDDEntityProcessor;
import io.mateu.intellij.plugin.processor.clazz.MateuMDDEntityProcessor1;
import org.jetbrains.annotations.NotNull;

public class DelombokMateuMDDEntityAction extends AbstractDelombokAction {

  @NotNull
  protected DelombokHandler createHandler() {
    return new DelombokHandler(ServiceManager.getService(MateuMDDEntityProcessor1.class), ServiceManager.getService(MateuMDDEntityProcessor.class));
  }
}
