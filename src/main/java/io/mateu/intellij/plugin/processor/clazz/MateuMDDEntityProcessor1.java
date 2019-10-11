package io.mateu.intellij.plugin.processor.clazz;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.*;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Plushnikov Michail
 */
public class MateuMDDEntityProcessor1 extends AbstractClassProcessor {

  private static final Logger log = Logger.getInstance(MateuMDDEntityProcessor1.class.getName());

  public MateuMDDEntityProcessor1() {
    super(PsiField.class, MateuMDDEntity.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum()) {
      builder.addError("@MateuMDDEntity is legal only on classes");
      result = false;
    }
    return result;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {

  log.info("procesando " + psiClass.getName());


    boolean yaTieneId = MateuMDDEntityProcessor.getIdFields(psiClass).size() > 0;
    if (!yaTieneId) {
      target.add(createIdField(psiClass, psiAnnotation));
    }



    boolean yaTieneVersion = getVersionFields(psiClass).size() > 0;
    if (!yaTieneVersion) {
      target.add(createVersionField(psiClass, psiAnnotation));
    }

  }

  public List<PsiField> getVersionFields(PsiClass psiClass) {
    List<PsiField> l = new ArrayList<>();
    for (PsiField field : psiClass.getAllFields()) {
      if (field.getAnnotation("javax.persistence.Version") != null || field.getAnnotation("Version") != null) l.add(field);
    }
    return l;
  }




  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }


  @NotNull
  public PsiField createIdField(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    log.info("añadiendo campo id");
      LombokLightFieldBuilder fieldBuilder = new LombokLightFieldBuilder(psiClass.getManager(), "id", PsiType.LONG)
        .withContainingClass(psiClass)
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(psiAnnotation);
      fieldBuilder.getModifierList().addAnnotation("javax.persistence.Id");

      PsiAnnotation a = fieldBuilder.getModifierList().addAnnotation("javax.persistence.GeneratedValue");
      //PsiEnumConstant e = (PsiEnumConstant) JavaPsiFacade.getInstance(psiClass.getProject()).findClass("javax.persistence.GeneratedValue", GlobalSearchScope.EMPTY_SCOPE);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      PsiAnnotation x = elementFactory.createAnnotationFromText("@javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.IDENTITY)", psiClass);
      a.setDeclaredAttributeValue("strategy", x.findAttributeValue("strategy"));

      return fieldBuilder;
  }

  private PsiField createVersionField(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    log.info("añadiendo campo version");
    LombokLightFieldBuilder fieldBuilder = new LombokLightFieldBuilder(psiClass.getManager(), "__version", PsiType.INT)
      .withContainingClass(psiClass)
      .withModifier(PsiModifier.PRIVATE)
      .withNavigationElement(psiAnnotation);
    fieldBuilder.getModifierList().addAnnotation("javax.persistence.Version");


    return fieldBuilder;//fieldBuilder.getAnnotations()[0].getQualifiedName()
  }

}
