package io.mateu.intellij.plugin.processor.clazz;

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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Plushnikov Michail
 */
public class MateuMDDEntityProcessor extends AbstractClassProcessor {

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

    createVersionField(psiClass);

    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Getter.class)) {
      target.addAll(getterProcessor.createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Setter.class)) {
      target.addAll(setterProcessor.createFieldSetters(psiClass, PsiModifier.PUBLIC));
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
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, JPAEqualsAndHashCode.class)) {
      target.addAll(equalsAndHashCodeProcessor.createEqualAndHashCode(psiClass, psiAnnotation));
    }

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

  private PsiMethod createToStringMethod(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    final PsiManager psiManager = psiClass.getManager();

    String blockText = "return this.getClass().getSimpleName();";
    if (isMethodPresent(psiClass, "getName")) {
      blockText = "return this.getName();";
    } else {
      EqualsAndHashCodeToStringHandler handler = jpaEqualsAndHashCodeProcessor.getHandler();
      final Collection<EqualsAndHashCodeToStringHandler.MemberInfo> memberInfos = handler.filterFields(psiClass, psiAnnotation, true, jpaEqualsAndHashCodeProcessor.INCLUDE_ANNOTATION_METHOD).stream().filter(mi -> mi.getField().getAnnotation("javax.persistence.Id") != null).collect(Collectors.toList());

      if (memberInfos.size() > 0) {

        blockText = "return \"\"";

        int pos = 0;
        for (EqualsAndHashCodeToStringHandler.MemberInfo memberInfo : memberInfos) {
          final String memberAccessor = handler.getMemberAccessorName(memberInfo, false, psiClass);
          if (pos++ == 0) blockText += " + \" \" ";
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


  @NotNull
  public PsiField createVersionField(@NotNull PsiClass psiClass) {
    LombokLightFieldBuilder fieldBuilder = new LombokLightFieldBuilder(psiClass.getManager(), "__version", PsiType.INT);
    fieldBuilder.getModifierList().addAnnotation("javax.persistence.Version");
    return fieldBuilder;
  }

}
