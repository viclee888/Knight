package com.zjutkz.processor;


import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.zjutkz.AbsKnightDress;
import com.zjutkz.annotation.Knight;
import com.zjutkz.info.ComponentInfo;
import com.zjutkz.info.KnightInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

/**
 * Created by kangzhe on 16/4/2.
 */
@SupportedAnnotationTypes("com.zjutkz.annotation.Knight")
public class KnightProcessor extends AbstractProcessor {

    private static int justOneTime = 1;

    Elements elementUtils;

    private Map<String,KnightInfo> mInfoMap = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv)
    {
        super.init(processingEnv);
        // 元素操作的辅助类
        elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if(justOneTime++ > 1){
            return false;
        }

        String className = "";
        String packageName = "";

        for(Element element : roundEnv.getElementsAnnotatedWith(Knight.class)) {
            if (element.getKind() == ElementKind.FIELD) {
                VariableElement varElement = (VariableElement) element;

                TypeElement classElement = (TypeElement) element.getEnclosingElement();

                PackageElement packageElement = elementUtils.getPackageOf(classElement);

                packageName = packageElement.getQualifiedName().toString();

                className = getClassName(classElement, packageName);

                String resNames[] = varElement.getAnnotation(Knight.class).resName().split(",");
                int[] nightResIds = varElement.getAnnotation(Knight.class).nightResId();
                int[] dayResIds = varElement.getAnnotation(Knight.class).dayResId();

                if(resNames.length != nightResIds.length || resNames.length != dayResIds.length){
                    throw new RuntimeException("The numbers of arguments you set are not consistence!");
                }

                String fieldName = varElement.getSimpleName().toString();
                String fieldType = varElement.asType().toString();

                ComponentInfo componentInfo = new ComponentInfo(fieldName,fieldType);
                for(int i = 0;i < resNames.length;i++){
                    String resName = resNames[i];
                    int nightResId = nightResIds[i];
                    int dayResId = dayResIds[i];
                    componentInfo.setRes(resName,nightResId,dayResId);
                }

                KnightInfo info = mInfoMap.get(className);
                if(info == null){
                    info = new KnightInfo(packageName,className);
                    mInfoMap.put(className, info);
                }
                mInfoMap.get(className).setComponent(componentInfo);
            }
        }

        for(String clzName : mInfoMap.keySet()){

            KnightInfo info = mInfoMap.get(clzName);

            List<FieldSpec> fieldList = new ArrayList<>();

            for(ComponentInfo componentInfo : info.getComponentInfoList()){
                try {
                    Class componentClz = convertToClass(componentInfo.getType());
                    FieldSpec componentSpec = FieldSpec.builder(componentClz, componentInfo.getName())
                            .addModifiers(Modifier.PRIVATE)
                            .build();
                    fieldList.add(componentSpec);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

            String changeToNightMethod = generateChangeToNightMethod(info);

            MethodSpec changeToNight = MethodSpec.methodBuilder("changeToNight")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement(changeToNightMethod)
                    .build();

            String changeToDayMethod = generateChangeToDayMethod(info);

            MethodSpec changeToDay = MethodSpec.methodBuilder("changeToDay")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement(changeToDayMethod)
                    .build();

            try {
                TypeSpec.Builder builder = TypeSpec.classBuilder(mInfoMap.get(clzName).getKnightClassName())
                        .superclass(AbsKnightDress.class)
                        .addModifiers(Modifier.PUBLIC);
                for(FieldSpec spec : fieldList){
                    builder.addField(spec);
                }

                builder.addMethod(changeToNight).addMethod(changeToDay);

                TypeSpec knightClz = builder.build();

                JavaFile javaFile = JavaFile.builder(mInfoMap.get(clzName).getPackageName(), knightClz)
                        .build();

                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    private String generateChangeToDayMethod(KnightInfo knightInfo) {
        String method = "";

        String contextClassName = knightInfo.getClassName();

        List<ComponentInfo> componentInfoList = knightInfo.getComponentInfoList();

        for(ComponentInfo info : componentInfoList){
            String name = info.getName();
            Map<String,int[]> resMap = info.getResMap();

            for(String resName : resMap.keySet()){
                int dayRes = resMap.get(resName)[1];

                switch(resName){
                    case "background":
                        method += "((" + contextClassName + ")context)." + name + ".setBackground(context.getResources().getDrawable(" + dayRes + "));\n";
                        break;
                    case "src":
                        method += "((" + contextClassName + ")context)." + name + ".setImageDrawable(context.getResources().getDrawable(" + dayRes + "));\n";
                        break;
                    case "textColor":
                        method += "((" + contextClassName + ")context)." + name + ".setTextColor(context.getResources().getColor(" + dayRes + "));\n";
                        break;
                }
            }
        }

        return method;
    }

    private String generateChangeToNightMethod(KnightInfo knightInfo) {
        String method = "";

        String contextClassName = knightInfo.getClassName();

        List<ComponentInfo> componentInfoList = knightInfo.getComponentInfoList();

        for(ComponentInfo info : componentInfoList){
            String name = info.getName();
            Map<String,int[]> resMap = info.getResMap();

            for(String resName : resMap.keySet()){
                int nightRes = resMap.get(resName)[0];

                switch(resName){
                    case "background":
                        method += "((" + contextClassName + ")context)." + name + ".setBackground(context.getResources().getDrawable(" + nightRes + "));\n";
                        break;
                    case "src":
                        method += "((" + contextClassName + ")context)." + name + ".setImageDrawable(context.getResources().getDrawable(" + nightRes + "));\n";
                        break;
                    case "textColor":
                        method += "((" + contextClassName + ")context)." + name + ".setTextColor(context.getResources().getColor(" + nightRes + "));\n";
                        break;
                }
            }
        }

        return method;
    }

    private String getClassName(TypeElement type, String packageName) {
        int packageLen = packageName.length() + 1;
        return type.getQualifiedName().toString().substring(packageLen)
                .replace('.', '$');
    }

    private Class convertToClass(String clzName) throws ClassNotFoundException {
        return Class.forName(clzName);
    }
}