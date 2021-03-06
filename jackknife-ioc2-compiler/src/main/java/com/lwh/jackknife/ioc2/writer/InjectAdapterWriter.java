/*
 * Copyright (C) 2018 The JackKnife Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lwh.jackknife.ioc2.writer;

import com.lwh.jackknife.ioc2.ViewInjector;
import com.lwh.jackknife.ioc2.annotation.ContentView;
import com.lwh.jackknife.ioc2.annotation.ViewInject;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class InjectAdapterWriter implements AdapterWriter {
	
	protected ProcessingEnvironment mProcessingEnv;
	protected Filer mFiler;
	
	public InjectAdapterWriter(ProcessingEnvironment mProcessingEnv) {
		this.mProcessingEnv = mProcessingEnv;
		this.mFiler = mProcessingEnv.getFiler();
	}

	@Override
	public void generate(Map<String, List<Element>> map) {
		Iterator<Entry<String, List<Element>>> iterator = map.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, List<Element>> entry = iterator.next();
			List<Element> cacheElements = entry.getValue();
			if (cacheElements == null || cacheElements.size() == 0) {
				continue;
			}
			Element element = cacheElements.get(0);
			InjectInfo info = createInjectInfo(element);
			ParameterSpec parameterSpec =
					ParameterSpec.builder(ClassName.get(Object.class), "target").build();
			ContentView contentView = element.getEnclosingElement().getAnnotation(ContentView.class);
			int layoutId = -1;
			if (contentView != null) {
				layoutId = contentView.value();
			}
			MethodSpec.Builder injectBuilder = MethodSpec.methodBuilder("inject")
					.addModifiers(Modifier.PUBLIC)
					.returns(void.class)
					.addParameter(parameterSpec)
					.addStatement(info.className+" activity = ("+info.className+")target;")
					.addStatement("activity.setContentView("+layoutId+")");
			for (int i=0;i<cacheElements.size();i++) {
				Element e = cacheElements.get(i);
				info = createInjectInfo(e);
				ViewInject viewInject = e.getAnnotation(ViewInject.class);
				String fieldName = e.getSimpleName().toString();
				int id = viewInject.value();
				injectBuilder.addStatement("activity."+fieldName+" =  $T.findViewById(activity, "+id+");", ClassName.bestGuess("com.lwh.jackknife.ioc2.ViewFinder"));
			}
			MethodSpec inject = injectBuilder.build();
			TypeSpec injectAdapter = TypeSpec.classBuilder(info.newClassName)
					.addSuperinterface(ClassName.bestGuess("com.lwh.jackknife.ioc2.adapter.InjectAdapter"))
					.addModifiers(Modifier.PUBLIC)
					.addMethod(inject)
					.build();
			try {
				JavaFile javaFile = JavaFile.builder(info.packageName, injectAdapter)
						.addFileComment("These codes are generated by JKNF automatically. Do not modify!\n")
						.build();
				javaFile.writeTo(mFiler);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String getPackageName(ProcessingEnvironment env,Element element) {
		return env.getElementUtils().getPackageOf(element).getQualifiedName().toString();
	}

	private InjectInfo createInjectInfo(Element element) {
		TypeElement typeElement = (TypeElement)element.getEnclosingElement();
		String packageName = getPackageName(mProcessingEnv, element);
		String className = typeElement.getSimpleName().toString();
		return new InjectInfo(packageName, className);
	}

	private class InjectInfo {

		public String packageName;
		public String className;
		public String newClassName;
		
		public InjectInfo(String packageName, String className) {
			this.packageName = packageName;
			this.className = className;
			this.newClassName = className + ViewInjector.SUFFIX;
		}
	}
}