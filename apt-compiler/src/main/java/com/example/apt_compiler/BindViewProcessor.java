package com.example.apt_compiler;

import com.example.apt_annotation.BindView;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("com.example.apt_annotation.BindView")
@AutoService(Processor.class)
public class BindViewProcessor extends AbstractProcessor {

    private static final String _SUFFIX = "$$Autobind";

    private static final String CONST_PARAM_TARGET_NAME = "target";

    //MainActivity substitute = (MainActivity)target;
    private static final String TARGET_ASSIGN_FORMAT = "%s substitute = (%s)target";

    //substitute.testTextView = substitute.findViewById(2131165315);
    private static final String TARGET_STATEMENT_FORMAT = "substitute.%s = substitute.findViewById(%d)";


    private Filer mFilerUtils;       // 文件管理工具类
    private Types mTypesUtils;    // 类型处理工具类
    private Elements mElementsUtils;  // Element处理工具类

    private Map<TypeElement, Set<ViewInfo>> mToBindMap = new HashMap<>(); //用于记录需要绑定的View的名称和对应的id

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mFilerUtils = processingEnv.getFiler();
        mTypesUtils = processingEnv.getTypeUtils();
        mElementsUtils = processingEnv.getElementUtils();
    }

    /*
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(BindView.class.getCanonicalName());
        return supportTypes; //将要支持的注解放入其中
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();// 表示支持最新的Java版本
    }
    */

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        System.out.println("start process");
        if (set != null && set.size() != 0) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(BindView.class);//获得被BindView注解标记的element

            categories(elements);//对不同的Activity进行分类

            //对不同的Activity生成不同的帮助类
            for (TypeElement typeElement : mToBindMap.keySet()) {
                generateCodeByPoet(typeElement);
            }
            return true;
        }
        return false;
    }

    private void categories(Set<? extends Element> elements) {
        for (Element element : elements) {  //遍历每一个element
            VariableElement variableElement = (VariableElement) element;    //被@BindView标注的应当是变量，这里简单的强制类型转换
            TypeElement enclosingElement = (TypeElement) variableElement.getEnclosingElement(); //获取代表Activity的TypeElement
            Set<ViewInfo> views = mToBindMap.get(enclosingElement); //views储存着一个Activity中将要绑定的view的信息
            if (views == null) {    //如果views不存在就new一个
                views = new HashSet<>();
                mToBindMap.put(enclosingElement, views);
            }
            BindView bindAnnotation = variableElement.getAnnotation(BindView.class);    //获取到一个变量的注解
            int id = bindAnnotation.value();    //取出注解中的value值，这个值就是这个view要绑定的xml中的id
            views.add(new ViewInfo(variableElement.getSimpleName().toString(), id));    //把要绑定的View的信息存进views中
        }
    }

    private void generateCodeByPoet(TypeElement typeElement) {

        String rawClassName = typeElement.getSimpleName().toString(); //获取要绑定的View所在类的名称
        String packageName = mElementsUtils.getPackageOf(typeElement).getQualifiedName().toString(); //获取要绑定的View所在类的包名

        MethodSpec.Builder builder = MethodSpec.methodBuilder("inject")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.OBJECT, CONST_PARAM_TARGET_NAME)
                .addStatement(String.format(TARGET_ASSIGN_FORMAT, rawClassName, rawClassName));


        for (ViewInfo viewInfo : mToBindMap.get(typeElement)) { //遍历每一个需要绑定的view
            builder.addStatement(String.format(TARGET_STATEMENT_FORMAT, viewInfo.viewName, viewInfo.id));
//            builder.addStatement(TARGET_STATEMENT_FORMAT, viewInfo.viewName, viewInfo.id);//为什么这样写会报错？
        }

        TypeSpec binder = TypeSpec.classBuilder(rawClassName + _SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ClassName.get("com.example.apt_api.template", "IBindHelper"))
                .addMethod(builder.build())
                .build();


        JavaFile javaFile = JavaFile.builder(packageName, binder).build();
        try {
            javaFile.writeTo(mFilerUtils);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    //要绑定的View的信息载体
    class ViewInfo {
        String viewName;    //view的变量名
        int id; //xml中的id

        public ViewInfo(String viewName, int id) {
            this.viewName = viewName;
            this.id = id;
        }
    }
}


