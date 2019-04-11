/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.FeatureStatus.DISABLED;
import static dagger.internal.codegen.FeatureStatus.ENABLED;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.EMIT_MODIFIABLE_METADATA_ANNOTATIONS;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_ANDROID_MODE;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.FAST_INIT;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.FLOATING_BINDS_METHODS;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.FORCE_USE_SERIALIZED_COMPONENT_IMPLEMENTATIONS;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.FORMAT_GENERATED_SOURCE;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Feature.WRITE_PRODUCER_NAME_IN_TOKEN;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.KeyOnlyOption.HEADER_COMPILATION;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.KeyOnlyOption.USE_GRADLE_INCREMENTAL_PROCESSING;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Validation.DISABLE_INTER_COMPONENT_SCOPE_VALIDATION;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Validation.EXPLICIT_BINDING_CONFLICTS_WITH_INJECT;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Validation.MODULE_BINDING_VALIDATION;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Validation.MODULE_HAS_DIFFERENT_SCOPES_VALIDATION;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Validation.NULLABLE_VALIDATION;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Validation.PRIVATE_MEMBER_VALIDATION;
import static dagger.internal.codegen.ProcessingEnvironmentCompilerOptions.Validation.STATIC_MEMBER_VALIDATION;
import static dagger.internal.codegen.ValidationType.ERROR;
import static dagger.internal.codegen.ValidationType.NONE;
import static dagger.internal.codegen.ValidationType.WARNING;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import dagger.producers.Produces;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

final class ProcessingEnvironmentCompilerOptions extends CompilerOptions {
  /** Returns a valid {@link CompilerOptions} parsed from the processing environment. */
  static CompilerOptions create(ProcessingEnvironment processingEnvironment) {
    return new ProcessingEnvironmentCompilerOptions(processingEnvironment).checkValid();
  }

  private final ProcessingEnvironment processingEnvironment;
  private final Map<EnumOption<?>, Object> enumOptions = new HashMap<>();

  private ProcessingEnvironmentCompilerOptions(ProcessingEnvironment processingEnvironment) {
    this.processingEnvironment = processingEnvironment;
  }

  @Override
  boolean usesProducers() {
    return processingEnvironment.getElementUtils().getTypeElement(Produces.class.getCanonicalName())
        != null;
  }

  @Override
  boolean headerCompilation() {
    return isEnabled(HEADER_COMPILATION);
  }

  @Override
  boolean fastInit() {
    return isEnabled(FAST_INIT);
  }

  @Override
  boolean formatGeneratedSource() {
    return isEnabled(FORMAT_GENERATED_SOURCE);
  }

  @Override
  boolean writeProducerNameInToken() {
    return isEnabled(WRITE_PRODUCER_NAME_IN_TOKEN);
  }

  @Override
  Diagnostic.Kind nullableValidationKind() {
    return diagnosticKind(NULLABLE_VALIDATION);
  }

  @Override
  Diagnostic.Kind privateMemberValidationKind() {
    return diagnosticKind(PRIVATE_MEMBER_VALIDATION);
  }

  @Override
  Diagnostic.Kind staticMemberValidationKind() {
    return diagnosticKind(STATIC_MEMBER_VALIDATION);
  }

  @Override
  boolean ignorePrivateAndStaticInjectionForComponent() {
    return isEnabled(IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT);
  }

  @Override
  ValidationType scopeCycleValidationType() {
    return parseOption(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION);
  }

  @Override
  boolean warnIfInjectionFactoryNotGeneratedUpstream() {
    return isEnabled(WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM);
  }

  @Override
  boolean aheadOfTimeSubcomponents() {
    return isEnabled(EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS);
  }

  @Override
  boolean forceUseSerializedComponentImplementations() {
    return isEnabled(FORCE_USE_SERIALIZED_COMPONENT_IMPLEMENTATIONS);
  }

  @Override
  boolean emitModifiableMetadataAnnotations() {
    return isEnabled(EMIT_MODIFIABLE_METADATA_ANNOTATIONS);
  }

  @Override
  boolean useGradleIncrementalProcessing() {
    return isEnabled(USE_GRADLE_INCREMENTAL_PROCESSING);
  }

  @Override
  ValidationType moduleBindingValidationType(TypeElement element) {
    return moduleBindingValidationType();
  }

  private ValidationType moduleBindingValidationType() {
    return parseOption(MODULE_BINDING_VALIDATION);
  }

  @Override
  Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind() {
    return diagnosticKind(MODULE_HAS_DIFFERENT_SCOPES_VALIDATION);
  }

  @Override
  ValidationType explicitBindingConflictsWithInjectValidationType() {
    return parseOption(EXPLICIT_BINDING_CONFLICTS_WITH_INJECT);
  }

  private boolean isEnabled(KeyOnlyOption keyOnlyOption) {
    return processingEnvironment.getOptions().containsKey(keyOnlyOption.toString());
  }

  private boolean isEnabled(Feature feature) {
    return parseOption(feature).equals(ENABLED);
  }

  private Diagnostic.Kind diagnosticKind(Validation validation) {
    return parseOption(validation).diagnosticKind().get();
  }

  @SuppressWarnings("CheckReturnValue")
  private ProcessingEnvironmentCompilerOptions checkValid() {
    for (KeyOnlyOption keyOnlyOption : KeyOnlyOption.values()) {
      isEnabled(keyOnlyOption);
    }
    for (Feature feature : Feature.values()) {
      parseOption(feature);
    }
    for (Validation validation : Validation.values()) {
      parseOption(validation);
    }
    noLongerRecognized(EXPERIMENTAL_ANDROID_MODE);
    noLongerRecognized(FLOATING_BINDS_METHODS);
    return this;
  }

  private void noLongerRecognized(CommandLineOption commandLineOption) {
    if (processingEnvironment.getOptions().containsKey(commandLineOption.toString())) {
      processingEnvironment
          .getMessager()
          .printMessage(
              Diagnostic.Kind.WARNING, commandLineOption + " is no longer recognized by Dagger");
    }
  }

  private interface CommandLineOption {
    /** The key of the option (appears after "-A"). */
    @Override
    String toString();
  }

  /** An option that can be set on the command line. */
  private interface EnumOption<E extends Enum<E>> extends CommandLineOption {
    /** The default value for this option. */
    E defaultValue();

    /** The valid values for this option. */
    Set<E> validValues();
  }

  enum KeyOnlyOption implements CommandLineOption {
    HEADER_COMPILATION {
      @Override
      public String toString() {
        return "experimental_turbine_hjar";
      }
    },

    USE_GRADLE_INCREMENTAL_PROCESSING {
      @Override
      public String toString() {
        return "dagger.gradle.incremental";
      }
    },
  }

  /**
   * A feature that can be enabled or disabled on the command line by setting {@code -Akey=ENABLED}
   * or {@code -Akey=DISABLED}.
   */
  enum Feature implements EnumOption<FeatureStatus> {
    FAST_INIT,

    EXPERIMENTAL_ANDROID_MODE,

    FORMAT_GENERATED_SOURCE(ENABLED),

    WRITE_PRODUCER_NAME_IN_TOKEN,

    WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM,

    IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT,

    EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS,

    FORCE_USE_SERIALIZED_COMPONENT_IMPLEMENTATIONS,

    EMIT_MODIFIABLE_METADATA_ANNOTATIONS(ENABLED),

    FLOATING_BINDS_METHODS,
    ;

    final FeatureStatus defaultValue;

    Feature() {
      this(DISABLED);
    }

    Feature(FeatureStatus defaultValue) {
      this.defaultValue = defaultValue;
    }

    @Override
    public FeatureStatus defaultValue() {
      return defaultValue;
    }

    @Override
    public Set<FeatureStatus> validValues() {
      return EnumSet.allOf(FeatureStatus.class);
    }

    @Override
    public String toString() {
      return optionName(this);
    }
  }

  /** The diagnostic kind or validation type for a kind of validation. */
  enum Validation implements EnumOption<ValidationType> {
    DISABLE_INTER_COMPONENT_SCOPE_VALIDATION(),

    NULLABLE_VALIDATION(ERROR, WARNING),

    PRIVATE_MEMBER_VALIDATION(ERROR, WARNING),

    STATIC_MEMBER_VALIDATION(ERROR, WARNING),

    /** Whether to validate partial binding graphs associated with modules. */
    MODULE_BINDING_VALIDATION(NONE, ERROR, WARNING),

    /**
     * How to report conflicting scoped bindings when validating partial binding graphs associated
     * with modules.
     */
    MODULE_HAS_DIFFERENT_SCOPES_VALIDATION(ERROR, WARNING),

    /**
     * How to report that an explicit binding in a subcomponent conflicts with an {@code @Inject}
     * constructor used in an ancestor component.
     */
    EXPLICIT_BINDING_CONFLICTS_WITH_INJECT(WARNING, ERROR, NONE),
    ;

    final ValidationType defaultType;
    final ImmutableSet<ValidationType> validTypes;

    Validation() {
      this(ERROR, WARNING, NONE);
    }

    Validation(ValidationType defaultType, ValidationType... moreValidTypes) {
      this.defaultType = defaultType;
      this.validTypes = immutableEnumSet(defaultType, moreValidTypes);
    }

    @Override
    public ValidationType defaultValue() {
      return defaultType;
    }

    @Override
    public Set<ValidationType> validValues() {
      return validTypes;
    }

    @Override
    public String toString() {
      return optionName(this);
    }
  }

  private static String optionName(Enum<? extends EnumOption<?>> option) {
    return "dagger." + UPPER_UNDERSCORE.to(LOWER_CAMEL, option.name());
  }

  /** The supported command-line options. */
  static ImmutableSet<String> supportedOptions() {
    // need explicit type parameter to avoid a runtime stream error
    return Stream.<CommandLineOption[]>of(
            KeyOnlyOption.values(), Feature.values(), Validation.values())
        .flatMap(Arrays::stream)
        .map(CommandLineOption::toString)
        .collect(toImmutableSet());
  }

  /** Returns the value for the option as set on the command line, or the default value if not. */
  private <T extends Enum<T>> T parseOption(EnumOption<T> option) {
    @SuppressWarnings("unchecked") // we only put covariant values into the map
    T value = (T) enumOptions.computeIfAbsent(option, this::parseOptionUncached);
    return value;
  }

  private <T extends Enum<T>> T parseOptionUncached(EnumOption<T> option) {
    String key = option.toString();
    if (processingEnvironment.getOptions().containsKey(key)) {
      String stringValue = processingEnvironment.getOptions().get(key);
      if (stringValue == null) {
        processingEnvironment
            .getMessager()
            .printMessage(Diagnostic.Kind.ERROR, "Processor option -A" + key + " needs a value");
      } else {
        try {
          T value =
              Enum.valueOf(
                  option.defaultValue().getDeclaringClass(), Ascii.toUpperCase(stringValue));
          if (option.validValues().contains(value)) {
            return value;
          }
        } catch (IllegalArgumentException e) {
          // handled below
        }
        processingEnvironment
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                String.format(
                    "Processor option -A%s may only have the values %s "
                        + "(case insensitive), found: %s",
                    key, option.validValues(), stringValue));
      }
    }
    return option.defaultValue();
  }
}
