package io.mateu.intellij.plugin.action.lombok;

import com.intellij.psi.PsiClass;
import de.plushnikov.intellij.plugin.action.lombok.*;
import lombok.*;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Entity;

public class LombokMateuMDDEntityHandler extends BaseLombokHandler {

  private final BaseLombokHandler[] handlers;

  public LombokMateuMDDEntityHandler() {
    handlers = new BaseLombokHandler[]{
      new LombokGetterHandler(), new LombokSetterHandler(),
      new LombokToStringHandler(), new LombokEqualsAndHashcodeHandler()};
  }

  public void processClass(@NotNull PsiClass psiClass) {
    for (BaseLombokHandler handler : handlers) {
      handler.processClass(psiClass);
    }

    removeDefaultAnnotation(psiClass, Getter.class);
    removeDefaultAnnotation(psiClass, Setter.class);
    //removeDefaultAnnotation(psiClass, ToString.class);
    removeDefaultAnnotation(psiClass, EqualsAndHashCode.class);
    removeDefaultAnnotation(psiClass, JPAEqualsAndHashCode.class);

    addAnnotation(psiClass, MateuMDDEntity.class);
    addAnnotation(psiClass, Entity.class);

  }

}
