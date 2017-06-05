/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.synthetic.codegen

import org.jetbrains.kotlin.android.synthetic.AndroidConst
import org.jetbrains.kotlin.android.synthetic.codegen.AndroidExpressionCodegenExtension.Companion.shouldCacheResource
import org.jetbrains.kotlin.android.synthetic.descriptors.AndroidEntityOptionsProxy
import org.jetbrains.kotlin.android.synthetic.res.AndroidSyntheticProperty
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.types.lowerIfFlexible
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

class ResourcePropertyStackValue(
        val receiver: StackValue,
        val typeMapper: KotlinTypeMapper,
        val resource: PropertyDescriptor,
        val container: ClassDescriptor,
        val entityOptions: AndroidEntityOptionsProxy,
        val androidPackage: String
) : StackValue(typeMapper.mapType(resource.returnType!!)) {
    private val entityType get() = entityOptions.entityType

    init {
        assert(entityOptions.entityType != AndroidEntityType.UNKNOWN)
    }

    override fun putSelector(type: Type, v: InstructionAdapter) {
        val returnTypeString = typeMapper.mapType(resource.type.lowerIfFlexible()).className
        if (AndroidConst.FRAGMENT_FQNAME == returnTypeString || AndroidConst.SUPPORT_FRAGMENT_FQNAME == returnTypeString) {
            return putSelectorForFragment(v)
        }

        val syntheticProperty = resource as AndroidSyntheticProperty

        if (entityOptions.cache.hasCache && shouldCacheResource(resource)) {
            val declarationDescriptorType = typeMapper.mapType(container)
            receiver.put(declarationDescriptorType, v)

            val resourceId = syntheticProperty.resource.id
            val packageName = resourceId.packageName ?: androidPackage
            v.getstatic(packageName.replace(".", "/") + "/R\$id", resourceId.name, "I")

            v.invokevirtual(declarationDescriptorType.internalName, AndroidExpressionCodegenExtension.CACHED_FIND_VIEW_BY_ID_METHOD_NAME, "(I)Landroid/view/View;", false)
        }
        else {
            when (entityType) {
                AndroidEntityType.ACTIVITY, AndroidEntityType.SUPPORT_FRAGMENT_ACTIVITY, AndroidEntityType.VIEW, AndroidEntityType.DIALOG -> {
                    receiver.put(Type.getType("L${entityType.internalClassName};"), v)
                    getResourceId(v)
                    v.invokevirtual(entityType.internalClassName, "findViewById", "(I)Landroid/view/View;", false)
                }
                AndroidEntityType.FRAGMENT, AndroidEntityType.SUPPORT_FRAGMENT -> {
                    receiver.put(Type.getType("L${entityType.internalClassName};"), v)
                    v.invokevirtual(entityType.internalClassName, "getView", "()Landroid/view/View;", false)
                    getResourceId(v)
                    v.invokevirtual("android/view/View", "findViewById", "(I)Landroid/view/View;", false)
                }
                AndroidEntityType.ENTITY -> {
                    receiver.put(Type.getType("L${entityType.internalClassName};"), v)
                    v.invokevirtual(entityType.internalClassName, "getEntityView", "()Landroid/view/View;", false)
                    getResourceId(v)
                    v.invokevirtual("android/view/View", "findViewById", "(I)Landroid/view/View;", false)
                }
                else -> throw IllegalStateException("Invalid Android class type: $entityType") // Should never occur
            }
        }

        v.checkcast(this.type)
    }

    private fun putSelectorForFragment(v: InstructionAdapter) {
        receiver.put(Type.getType("L${entityType.internalClassName};"), v)

        when (entityType) {
            AndroidEntityType.ACTIVITY, AndroidEntityType.FRAGMENT -> {
                v.invokevirtual(entityType.internalClassName, "getFragmentManager", "()Landroid/app/FragmentManager;", false)
                getResourceId(v)
                v.invokevirtual("android/app/FragmentManager", "findFragmentById", "(I)Landroid/app/Fragment;", false)
            }
            AndroidEntityType.SUPPORT_FRAGMENT -> {
                v.invokevirtual(entityType.internalClassName, "getFragmentManager", "()Landroid/support/v4/app/FragmentManager;", false)
                getResourceId(v)
                v.invokevirtual("android/support/v4/app/FragmentManager", "findFragmentById", "(I)Landroid/support/v4/app/Fragment;", false)
            }
            AndroidEntityType.SUPPORT_FRAGMENT_ACTIVITY -> {
                v.invokevirtual(entityType.internalClassName, "getSupportFragmentManager", "()Landroid/support/v4/app/FragmentManager;", false)
                getResourceId(v)
                v.invokevirtual("android/support/v4/app/FragmentManager", "findFragmentById", "(I)Landroid/support/v4/app/Fragment;", false)
            }
            else -> throw IllegalStateException("Invalid Android class type: $entityType") // Should never occur
        }

        v.checkcast(this.type)
    }

    fun getResourceId(v: InstructionAdapter) {
        v.getstatic(androidPackage.replace(".", "/") + "/R\$id", resource.name.asString(), "I")
    }
}