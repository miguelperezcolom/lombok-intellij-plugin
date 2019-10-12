package io.mateu.intellij.plugin.processor.clazz;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.*;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler;
import de.plushnikov.intellij.plugin.provider.LombokAugmentProvider;
import de.plushnikov.intellij.plugin.psi.LombokEnumConstantBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightIdentifier;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.*;
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
public class MateuMDDEntityProcessor extends AbstractClassProcessor {

  private static final Logger log = Logger.getInstance(MateuMDDEntityProcessor.class.getName());


  private final GetterProcessor getterProcessor;
  private final SetterProcessor setterProcessor;
  private final EqualsAndHashCodeProcessor equalsAndHashCodeProcessor;
  private final ToStringProcessor toStringProcessor;
  private final RequiredArgsConstructorProcessor requiredArgsConstructorProcessor;
  private final NoArgsConstructorProcessor noArgsConstructorProcessor;
  private final JPAEqualsAndHashCodeProcessor jpaEqualsAndHashCodeProcessor;

  public MateuMDDEntityProcessor(@NotNull GetterProcessor getterProcessor,
                                 @NotNull SetterProcessor setterProcessor,
                                 @NotNull EqualsAndHashCodeProcessor equalsAndHashCodeProcessor,
                                 @NotNull ToStringProcessor toStringProcessor,
                                 @NotNull RequiredArgsConstructorProcessor requiredArgsConstructorProcessor,
                                 @NotNull NoArgsConstructorProcessor noArgsConstructorProcessor,
                                 @NotNull JPAEqualsAndHashCodeProcessor jpaEqualsAndHashCodeProcessor) {
    super(PsiMethod.class, MateuMDDEntity.class);
    this.getterProcessor = getterProcessor;
    this.setterProcessor = setterProcessor;
    this.equalsAndHashCodeProcessor = equalsAndHashCodeProcessor;
    this.toStringProcessor = toStringProcessor;
    this.requiredArgsConstructorProcessor = requiredArgsConstructorProcessor;
    this.noArgsConstructorProcessor = noArgsConstructorProcessor;
    this.jpaEqualsAndHashCodeProcessor = jpaEqualsAndHashCodeProcessor;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final PsiAnnotation equalsAndHashCodeAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, EqualsAndHashCode.class);
    final PsiAnnotation jpaEqualsAndHashCodeAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, JPAEqualsAndHashCode.class);
    if (null == jpaEqualsAndHashCodeAnnotation && null == equalsAndHashCodeAnnotation) {
      equalsAndHashCodeProcessor.validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
    }

    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      requiredArgsConstructorProcessor.validateBaseClassConstructor(psiClass, builder);
    }

    return validateAnnotationOnRightType(psiClass, builder);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("'@MateuMDDEntity' is only supported on a class type");
      result = false;
    }
    return result;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {

  log.info("procesando " + psiClass.getName());


    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Getter.class)) {
      target.addAll(getterProcessor.createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }

    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Setter.class)) {
      target.addAll(setterProcessor.createFieldSetters(psiClass, PsiModifier.PUBLIC));
    }


    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, JPAEqualsAndHashCode.class)) {
      target.addAll(jpaEqualsAndHashCodeProcessor.createEqualAndHashCode(psiClass, psiAnnotation));
    }

/*
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, EqualsAndHashCode.class)) {
      target.addAll(equalsAndHashCodeProcessor.createEqualAndHashCode(psiClass, psiAnnotation));
    }

 */
    //if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, ToString.class)) {
    if (!isMethodDefined(psiClass, "toString")) {
      target.add(createToStringMethod(psiClass, psiAnnotation));
    }
    //}


    final boolean hasConstructorWithoutParamaters;
    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      target.addAll(requiredArgsConstructorProcessor.createRequiredArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName));
      // if there are no required field, it will already have a default constructor without parameters
      hasConstructorWithoutParamaters = requiredArgsConstructorProcessor.getRequiredFields(psiClass).isEmpty();
    } else {
      hasConstructorWithoutParamaters = false;
    }

    if (!hasConstructorWithoutParamaters && shouldGenerateNoArgsConstructor(psiClass, requiredArgsConstructorProcessor)) {
      target.addAll(noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PRIVATE, psiAnnotation, true));
    }

  }

  public static PsiField getFieldByName(PsiClass psiClass, String fieldName) {
    List<PsiField> l = new ArrayList<>();
    for (PsiField field : psiClass.getAllFields()) {
      if (fieldName.equalsIgnoreCase(field.getName())) return field;
    }
    return null;
  }

  public static List<PsiField> getIdFields(PsiClass psiClass) {
    List<PsiField> l = new ArrayList<>();
    for (PsiField field : psiClass.getAllFields()) {
      if (field.getAnnotation("javax.persistence.Id") != null || field.getAnnotation("Id") != null) l.add(field);
    }
    return l;
  }

  public static String getFieldAccessorName(@NotNull PsiField field, boolean doNotUseGetters, @NotNull PsiClass psiClass) {
    final String memberAccessor;
    memberAccessor = buildAttributeNameString(doNotUseGetters, field, psiClass);
    return memberAccessor;
  }

  public static String buildAttributeNameString(boolean doNotUseGetters, @NotNull PsiField classField, @NotNull PsiClass psiClass) {
    final String fieldName = classField.getName();
    if (doNotUseGetters) {
      return fieldName;
    } else {
      final String getterName = LombokUtils.getGetterName(classField);

      final boolean hasGetter;
      @SuppressWarnings("unchecked") final boolean annotatedWith = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, Data.class, Value.class, Getter.class);
      if (annotatedWith) {
        final PsiAnnotation getterLombokAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, Getter.class);
        hasGetter = null == getterLombokAnnotation || null != LombokProcessorUtil.getMethodModifier(getterLombokAnnotation);
      } else {
        hasGetter = PsiMethodUtil.hasMethodByName(PsiClassUtil.collectClassMethodsIntern(psiClass), getterName);
      }

      return hasGetter ? getterName + "()" : fieldName;
    }
  }

  private PsiMethod createToStringMethod(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    final PsiManager psiManager = psiClass.getManager();

    String blockText = "return this.getClass().getSimpleName();";
    PsiField nameField = getFieldByName(psiClass, "name");
    if (nameField != null) {
      blockText = "return \"\" + this.getName();";
    } else {

      List<PsiField> idFields = getIdFields(psiClass);

      if (idFields.size() > 0) {

        blockText = "return \"\"";

        int pos = 0;
        for (PsiField idField : idFields) {
          final String memberAccessor = getFieldAccessorName(idField, false, psiClass);
          if (pos++ > 0) blockText += " + \" \" ";
          blockText += " + " + memberAccessor;
        }

        blockText += ";";
      }

    }

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, "toString")
      .withMethodReturnType(PsiType.getJavaLangString(psiManager, GlobalSearchScope.allScope(psiClass.getProject())))
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC);
    methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(blockText, methodBuilder));
    return methodBuilder;
  }

  private boolean isMethodDefined(PsiClass psiClass, String methodName) {
    final Collection<PsiMethod> classMethodsIntern = PsiClassUtil.collectClassMethodsIntern(psiClass);
    return PsiMethodUtil.hasMethodByName(classMethodsIntern, methodName);
  }

  private boolean isMethodPresent(PsiClass psiClass, String methodName) {
    for (PsiMethod m : psiClass.getAllMethods()) {
      if (methodName.equals(m.getName())) return true;
    }
    return false;
  }

  private boolean shouldGenerateRequiredArgsConstructor(@NotNull PsiClass psiClass, @Nullable String staticName) {
    boolean result = false;
    // create required constructor only if there are no other constructor annotations
    @SuppressWarnings("unchecked") final boolean notAnnotatedWith = PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, NoArgsConstructor.class,
      RequiredArgsConstructor.class, AllArgsConstructor.class, Builder.class, SuperBuilder.class);
    if (notAnnotatedWith) {
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      filterToleratedElements(definedConstructors);

      // and only if there are no any other constructors!
      if (definedConstructors.isEmpty()) {
        final Collection<PsiField> requiredFields = requiredArgsConstructorProcessor.getRequiredFields(psiClass);

        result = requiredArgsConstructorProcessor.validateIsConstructorNotDefined(
          psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance());
      }
    }
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }

}
