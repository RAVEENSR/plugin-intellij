/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ballerinalang.plugins.idea.runconfig;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.ballerinalang.plugins.idea.psi.BallerinaFile;
import org.ballerinalang.plugins.idea.psi.FullyQualifiedPackageNameNode;
import org.ballerinalang.plugins.idea.psi.FunctionDefinitionNode;
import org.ballerinalang.plugins.idea.psi.PackageDeclarationNode;
import org.ballerinalang.plugins.idea.psi.ServiceDefinitionNode;
import org.ballerinalang.plugins.idea.runconfig.application.BallerinaApplicationConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BallerinaRunConfigurationProducerBase<T extends BallerinaRunConfigurationWithMain>
        extends RunConfigurationProducer<T> implements Cloneable {

    protected BallerinaRunConfigurationProducerBase(@NotNull ConfigurationType configurationType) {
        super(configurationType);
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull T configuration, @NotNull ConfigurationContext context,
                                                    Ref<PsiElement> sourceElement) {
        PsiFile file = getFileFromContext(context);
        if (file == null) {
            return false;
        }
        // Get the element. This will be an identifier element.
        PsiElement element = sourceElement.get();
        // Get the FunctionDefinitionNode parent from element (if exists).
        FunctionDefinitionNode functionNode = PsiTreeUtil.getParentOfType(element, FunctionDefinitionNode.class);
        // Get the ServiceDefinitionNode parent from element (if exists).
        ServiceDefinitionNode serviceDefinitionNode = PsiTreeUtil.getParentOfType(element, ServiceDefinitionNode.class);

        // Get the declared package in the file if available.
        String packageInFile = "";
        boolean isPackageDeclared = false;
        // Get the PackageDeclarationNode if available.
        PackageDeclarationNode packageDeclarationNode = PsiTreeUtil.findChildOfType(file, PackageDeclarationNode.class);
        if (packageDeclarationNode != null) {
            isPackageDeclared = true;
        }
        // Get the package path node. We need this to get the package path of the file.
        FullyQualifiedPackageNameNode fullyQualifiedPackageNameNode = PsiTreeUtil.findChildOfType
                (packageDeclarationNode, FullyQualifiedPackageNameNode.class);
        if (fullyQualifiedPackageNameNode != null) {
            // Regardless of the OS, separator character will be "/".
            packageInFile = fullyQualifiedPackageNameNode.getText().replaceAll("\\.", "/");
        }

        // Get existing configuration if available.
        RunnerAndConfigurationSettings existingConfigurations = context.findExisting();
        if (existingConfigurations != null) {
            // Get the RunConfiguration.
            RunConfiguration existingConfiguration = existingConfigurations.getConfiguration();
            // Run configuration might be an application configuration. So we need to check the type.
            if (existingConfiguration instanceof BallerinaApplicationConfiguration) {
                // Set other configurations.
                setConfigurations((BallerinaApplicationConfiguration) existingConfiguration, file, functionNode,
                        serviceDefinitionNode, packageInFile, isPackageDeclared);
                return true;
            }
        } else if (configuration instanceof BallerinaApplicationConfiguration) {
            // If an existing configuration is not found and the configuration provided is of correct type.
            String configName = getConfigurationName(file);
            // Set the config name. This will be the file name.
            configuration.setName(configName);
            // Set the file path.
            configuration.setFilePath(file.getVirtualFile().getPath());
            // Set the module.
            Module module = context.getModule();
            if (module != null) {
                configuration.setModule(module);
            }
            // Set other configurations.
            setConfigurations((BallerinaApplicationConfiguration) configuration, file, functionNode,
                    serviceDefinitionNode, packageInFile, isPackageDeclared);
            return true;
        }
        // Return false if the provided configuration type cannot be applied.
        return false;
    }

    private void setConfigurations(@NotNull BallerinaApplicationConfiguration configuration, @NotNull PsiFile file,
                                   @Nullable FunctionDefinitionNode functionNode,
                                   @Nullable ServiceDefinitionNode serviceDefinitionNode,
                                   @NotNull String packageInFile, boolean isPackageDeclared) {
        // Set the run kind.
        if (BallerinaRunUtil.hasMainFunction(file) && functionNode != null) {
            // Set the kind to MAIN.
            configuration.setRunKind(RunConfigurationKind.MAIN);
        } else if (BallerinaRunUtil.hasServices(file) && serviceDefinitionNode != null) {
            // Set the kind to SERVICE.
            configuration.setRunKind(RunConfigurationKind.SERVICE);
        }

        // Set the package.
        if (isPackageDeclared) {
            configuration.setPackage(packageInFile);
        } else {
            configuration.setPackage("");
        }

        // Set the working directory. If this is not set, package in sub modules will not run properly.
        Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module == null) {
            return;
        }
        VirtualFile moduleFile = module.getModuleFile();
        if (moduleFile == null) {
            return;
        }
        configuration.setWorkingDirectory(moduleFile.getParent().getPath());
    }

    @NotNull
    protected abstract String getConfigurationName(@NotNull PsiFile file);

    @Override
    public boolean isConfigurationFromContext(@NotNull T configuration, ConfigurationContext context) {
        BallerinaFile file = getFileFromContext(context);
        return file != null && FileUtil.pathsEqual(configuration.getFilePath(), file.getVirtualFile().getPath());
    }

    @Nullable
    private static BallerinaFile getFileFromContext(@Nullable ConfigurationContext context) {
        PsiElement contextElement = BallerinaRunUtil.getContextElement(context);
        PsiFile psiFile = contextElement != null ? contextElement.getContainingFile() : null;
        return psiFile instanceof BallerinaFile ? (BallerinaFile) psiFile : null;
    }
}
