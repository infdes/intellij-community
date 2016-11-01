/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.streamToLoop;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This class represents a variable which holds stream element. Its lifecycle is the following:
 * 1. Construction: fast, in case you don't need to perform a fix actually
 * 2. Gather name candidates (addBestNameCandidate/addOtherNameCandidate can be called).
 * 3. addCandidatesFromType called which adds name candidates based on type
 * (until this stage no changes in original file should be performed)
 * 4. Register variable in {@code StreamToLoopReplacementContext}: actual variable name is assigned here
 * 5. Usage in code generation: getName()/getType() could be called.
 *
 * @author Tagir Valeev
 */
class StreamVariable {
  private static final Logger LOG = Logger.getInstance(StreamVariable.class);

  static StreamVariable STUB = new StreamVariable(PsiType.VOID) {
    @Override
    public void addBestNameCandidate(String candidate) {
    }

    @Override
    public void addCandidatesFromType(JavaCodeStyleManager manager) {
    }

    @Override
    void register(StreamToLoopInspection.StreamToLoopReplacementContext context) {
    }

    @Override
    public String toString() {
      return "###STUB###";
    }
  };

  String myName;
  String myType;
  PsiType myPsiType;

  private Collection<String> myBestCandidates = new LinkedHashSet<>();
  private Collection<String> myOtherCandidates = new LinkedHashSet<>();

  StreamVariable(@NotNull PsiType type) {
    myPsiType = type;
  }

  /**
   * Register best name candidate for this variable (like lambda argument which was explicitly present in the original code).
   *
   * @param candidate name candidate
   */
  void addBestNameCandidate(String candidate) {
    myBestCandidates.add(candidate);
  }

  /**
   * Register normal name candidate for this variable (for example, derived using unpluralize from collection name, etc.)
   *
   * @param candidate name candidate
   */
  void addOtherNameCandidate(String candidate) {
    myOtherCandidates.add(candidate);
  }

  /**
   * Register name candidates based on variable type.
   * This must be called only once and only after addBestNameCandidate/addOtherNameCandidate.
   *
   * @param manager project-specific {@link JavaCodeStyleManager}
   */
  void addCandidatesFromType(JavaCodeStyleManager manager) {
    LOG.assertTrue(myPsiType != null);
    LOG.assertTrue(myType == null);
    myOtherCandidates.addAll(Arrays.asList(manager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, myPsiType, true).names));
    myType = myPsiType.getCanonicalText();
    myPsiType = null;
  }

  /**
   * Register variable within {@link com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.StreamToLoopReplacementContext}.
   * Must be called once after all name candidates are registered. Now variable gets an actual name.
   *
   * @param context context to use
   */
  void register(StreamToLoopInspection.StreamToLoopReplacementContext context) {
    LOG.assertTrue(myName == null);
    List<String> variants = StreamEx.of(myBestCandidates).append(myOtherCandidates).distinct().toList();
    if (variants.isEmpty()) variants.add("val");
    myName = context.registerVarName(variants);
    myBestCandidates = myOtherCandidates = null;
  }

  String getName() {
    LOG.assertTrue(myName != null);
    return myName;
  }

  String getType() {
    LOG.assertTrue(myType != null);
    return myType;
  }

  String getDeclaration() {
    return getType() + " " + getName();
  }

  @Override
  public String toString() {
    if (myName == null) {
      return "###(unregistered: " + myBestCandidates + "|" + myOtherCandidates + ")###";
    }
    return myName;
  }
}
