package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class BaseLombokHandler implements CodeInsightActionHandler {

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (file.isWritable()) {
      PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
      if (null != psiClass) {
        processClass(psiClass);

        UndoUtil.markPsiFileForUndo(file);
      }
    }
  }

  public abstract void processClass(@NotNull PsiClass psiClass);

  protected void processIntern(@NotNull Map<PsiField, PsiMethod> fieldMethodMap, @NotNull PsiClass psiClass, @NotNull Class<? extends Annotation> annotationClass) {
    if (fieldMethodMap.isEmpty()) {
      return;
    }

    final PsiMethod firstPropertyMethod = fieldMethodMap.values().iterator().next();

    final boolean useAnnotationOnClass = haveAllMethodsSameAccessLevel(fieldMethodMap.values()) &&
      isNotAnnotatedWithOrSameAccessLevelAs(psiClass, firstPropertyMethod, annotationClass);

    if (useAnnotationOnClass) {
      addAnnotation(psiClass, firstPropertyMethod, annotationClass);
    }

    for (Map.Entry<PsiField, PsiMethod> fieldMethodEntry : fieldMethodMap.entrySet()) {
      final PsiField propertyField = fieldMethodEntry.getKey();
      final PsiMethod propertyMethod = fieldMethodEntry.getValue();

      if (null != propertyField) {
        boolean isStatic = propertyField.hasModifierProperty(PsiModifier.STATIC);
        if (isStatic || !useAnnotationOnClass) {
          addAnnotation(propertyField, propertyMethod, annotationClass);
        }

        // Move all annotations to field declaration
        for (PsiAnnotation psiMethodAnnotation : propertyMethod.getModifierList().getAnnotations()) {
          psiClass.addBefore(psiMethodAnnotation, propertyField);
        }

        propertyMethod.delete();
      }
    }
  }

  private boolean isNotAnnotatedWithOrSameAccessLevelAs(PsiClass psiClass, PsiMethod firstPropertyMethod, Class<? extends Annotation> annotationClass) {
    final PsiAnnotation presentAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, annotationClass);
    if (null != presentAnnotation) {

      final String presentAccessModifier = LombokProcessorUtil.getMethodModifier(presentAnnotation);
      final String currentAccessModifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(firstPropertyMethod.getModifierList()));

      return (presentAccessModifier == null && currentAccessModifier == null) ||
        (presentAccessModifier != null && presentAccessModifier.equals(currentAccessModifier));
    }
    return true;
  }

  private boolean haveAllMethodsSameAccessLevel(Collection<PsiMethod> psiMethods) {
    final Set<Integer> accessLevelSet = new HashSet<>();
    for (PsiMethod psiMethod : psiMethods) {
      accessLevelSet.add(PsiUtil.getAccessLevel(psiMethod.getModifierList()));
    }
    return accessLevelSet.size() <= 1;
  }

  private void addAnnotation(@NotNull PsiModifierListOwner targetElement, @NotNull PsiModifierListOwner sourceElement,
                             @NotNull Class<? extends Annotation> annotationClass) {
    final PsiAnnotation newPsiAnnotation = LombokProcessorUtil.createAnnotationWithAccessLevel(annotationClass, sourceElement);

    addAnnotation(targetElement, newPsiAnnotation, annotationClass);
  }

  protected void addAnnotation(@NotNull PsiClass targetElement, @NotNull Class<? extends Annotation> annotationClass) {
    final PsiAnnotation newPsiAnnotation = PsiAnnotationUtil.createPsiAnnotation(targetElement, annotationClass);

    addAnnotation(targetElement, newPsiAnnotation, annotationClass);
  }

  private void addAnnotation(@NotNull PsiModifierListOwner targetElement, @NotNull PsiAnnotation newPsiAnnotation,
                             @NotNull Class<? extends Annotation> annotationClass) {
    final PsiAnnotation presentAnnotation = PsiAnnotationSearchUtil.findAnnotation(targetElement, annotationClass);

    final Project project = targetElement.getProject();
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    javaCodeStyleManager.shortenClassReferences(newPsiAnnotation);

    if (null == presentAnnotation) {
      PsiModifierList modifierList = targetElement.getModifierList();
      if (null != modifierList) {
        modifierList.addAfter(newPsiAnnotation, null);
      }
    } else {
      presentAnnotation.setDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME,
        newPsiAnnotation.findDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME));
    }
  }

  protected void removeDefaultAnnotation(@NotNull PsiModifierListOwner targetElement, @NotNull Class<? extends Annotation> annotationClass) {
    final PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(targetElement, annotationClass);
    if (null != psiAnnotation) {
      boolean hasOnlyDefaultValues = true;

      final PsiAnnotationParameterList psiAnnotationParameterList = psiAnnotation.getParameterList();
      for (PsiNameValuePair nameValuePair : psiAnnotationParameterList.getAttributes()) {
        if (null != psiAnnotation.findDeclaredAttributeValue(nameValuePair.getName())) {
          hasOnlyDefaultValues = false;
          break;
        }
      }

      if (hasOnlyDefaultValues) {
        psiAnnotation.delete();
      }
    }
  }

  @Nullable
  protected PsiMethod findPublicNonStaticMethod(@NotNull PsiClass psiClass, @NotNull String methodName, @NotNull PsiType returnType, PsiType... params) {
    final PsiMethod[] toStringMethods = psiClass.findMethodsByName(methodName, false);
    for (PsiMethod method : toStringMethods) {
      if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
        !method.hasModifierProperty(PsiModifier.STATIC) &&
        returnType.equals(method.getReturnType())) {

        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] psiParameters = parameterList.getParameters();
        final int paramsCount = params.length;

        if (psiParameters.length == paramsCount) {
          boolean allParametersFound = true;
          for (int i = 0; i < paramsCount; i++) {

            if (!psiParameters[i].getType().equals(params[i])) {
              allParametersFound = false;
              break;
            }
          }
          if (allParametersFound) {
            return method;
          }
        }
      }
    }
    return null;
  }
}
