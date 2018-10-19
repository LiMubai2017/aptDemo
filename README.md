# Android APT快速教程
## 简介
APT(Annotation Processing Tool)即**注解处理器**，是一种用来处理注解的工具。JVM会在**编译期**就运行APT去扫描处理代码中的注解然后输出java文件。  

![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/confused.jpg?raw=true)

简单来说~~就是你只需要添加注解，APT就可以帮你生成需要的代码

许多的Android开源库都使用了APT技术，如ButterKnife、ARouter、EventBus等


## 动手实现一个简单的APT
  
  
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/apt_steps.jpg?raw=true)


### 小目标
在使用Java开发Android时，页面初始化的时候我们通常要写大量的view = findViewById(R.id.xx)代码

作为一个优(lan)秀(duo)的程序员，我们现在就要实现一个APT来完成这个繁琐的工作，通过一个注解就可以自动给View获得实例  

[本demo地址](https://github.com/LiMubai2017/aptDemo)


### 第零步 创建一个项目
创建一个项目，名称叫 apt_demo  
创建一个Activity，名称叫 MainActivity  
在布局中添加一个TextView, id为test_textview  

<br/>

![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/example.jpg?raw=true)


### 第一步 自定义注解
创建一个Java Library Module名称叫 apt-annotation  

在这个module中创建自定义注解 @BindView
```
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface BindView {
    int value();
}
```

- @Retention(RetentionPolicy.CLASS)：表示这个注解保留到编译期
- @Target(ElementType.FIELD)：表示注解范围为类成员（构造方法、方法、成员变量）

### 第二步 实现APT Compiler
创建一个Java Library Module名称叫 apt-compiler  

在这个Module中添加依赖
```
dependencies {
    implementation project(':apt-annotation')
}
```
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/androidstudio_version.jpg?raw=true)

在这个Module中创建BindViewProcessor类  

直接给出代码~~
```
public class BindViewProcessor extends AbstractProcessor {
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

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        System.out.println("start process");
        if (set != null && set.size() != 0) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(BindView.class);//获得被BindView注解标记的element

            categories(elements);//对不同的Activity进行分类

            //对不同的Activity生成不同的帮助类
            for (TypeElement typeElement : mToBindMap.keySet()) {
                String code = generateCode(typeElement);    //获取要生成的帮助类中的所有代码
                String helperClassName = typeElement.getQualifiedName() + "$$Autobind"; //构建要生成的帮助类的类名

                //输出帮助类的java文件，在这个例子中就是MainActivity$$Autobind.java文件
                //输出的文件在build->source->apt->目录下
                try {
                    JavaFileObject jfo = mFilerUtils.createSourceFile(helperClassName, typeElement);
                    Writer writer = jfo.openWriter();
                    writer.write(code);
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            return true;
        }

        return false;
    }

    //将需要绑定的View按不同Activity进行分类
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

    //按不同的Activity生成不同的帮助类
    private String generateCode(TypeElement typeElement) {
        String rawClassName = typeElement.getSimpleName().toString(); //获取要绑定的View所在类的名称
        String packageName = ((PackageElement) mElementsUtils.getPackageOf(typeElement)).getQualifiedName().toString(); //获取要绑定的View所在类的包名
        String helperClassName = rawClassName + "$$Autobind";   //要生成的帮助类的名称

        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(packageName).append(";\n");   //构建定义包的代码
        builder.append("import com.example.apt_api.template.IBindHelper;\n\n"); //构建import类的代码

        builder.append("public class ").append(helperClassName).append(" implements ").append("IBindHelper");   //构建定义帮助类的代码
        builder.append(" {\n"); //代码格式，可以忽略
        builder.append("\t@Override\n");    //声明这个方法为重写IBindHelper中的方法
        builder.append("\tpublic void inject(" + "Object" + " target ) {\n");   //构建方法的代码
        for (ViewInfo viewInfo : mToBindMap.get(typeElement)) { //遍历每一个需要绑定的view
            builder.append("\t\t"); //代码格式，可以忽略
            builder.append(rawClassName + " substitute = " + "(" + rawClassName + ")" + "target;\n");    //强制类型转换

            builder.append("\t\t"); //代码格式，可以忽略
            builder.append("substitute." + viewInfo.viewName).append(" = ");    //构建赋值表达式
            builder.append("substitute.findViewById(" + viewInfo.id + ");\n");  //构建赋值表达式
        }
        builder.append("\t}\n");    //代码格式，可以忽略
        builder.append('\n');   //代码格式，可以忽略
        builder.append("}\n");  //代码格式，可以忽略

        return builder.toString();
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

```
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/too_long.jpg?raw=true)

![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/take_it_easy.jpg?raw=true)

#### 2-1 继承AbstractProcessor抽象类
我们自己实现的APT类需要继承AbstractProcessor这个类，其中需要重写以下方法：
- init(ProcessingEnvironment processingEnv)
- getSupportedAnnotationTypes()
- getSupportedSourceVersion()
- process(Set<? extends TypeElement> set, RoundEnvironment roundEnv)

#### 2-2 init(ProcessingEnvironment processingEnv)方法
这不是一个抽象方法，但progressingEnv参数给我们提供了许多有用的工具,所以我们需要重写它
```
@Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mFilerUtils = processingEnv.getFiler();
        mTypesUtils = processingEnv.getTypeUtils();
        mElementsUtils = processingEnv.getElementUtils();
    }
```
- mFilterUtils 文件管理工具类，在后面生成java文件时会用到
- mTypesUtils 类型处理工具类，本例不会用到
- mElementsUtils Element处理工具类,后面获取包名时会用到  

***限于篇幅就不展开对这几个工具的解析，读者可以自行查看文档~***

#### 2-3 getSupportedAnnotationTypes()
由方法名我们就可以看出这个方法是提供我们这个APT**能够处理的注解**

这也不是一个抽象方法，但仍需要重写它，否则会抛出异常（滑稽），至于为什么有兴趣可以自行查看源码~

这个方法有固定写法~
```
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(BindView.class.getCanonicalName());
        return supportTypes; //将要支持的注解放入其中
    }
```

#### 2-4 getSupportedSourceVersion()
顾名思义，就是提供我们这个APT支持的版本号

这个方法和上面的getSupportedAnnotationTypes()类似，也不是一个抽象方法，但也需要重写，也有固定的写法
```
@Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();// 表示支持最新的Java版本
    }
```

#### 2-5 process(Set<? extends TypeElement> set, RoundEnvironment roundEnv)
最主要的方法，用来处理注解,这也是唯一的抽象方法，有两个参数
- set 参数是要处理的注解的类型集合
- roundEnv 表示运行环境，可以通过这个参数获得被注解标注的代码块
```
@Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        System.out.println("start process");
        if (set != null && set.size() != 0) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(BindView.class);//获得被BindView注解标记的element

            categories(elements);//对不同的Activity进行分类

            //对不同的Activity生成不同的帮助类
            for (TypeElement typeElement : mToBindMap.keySet()) {
                String code = generateCode(typeElement);    //获取要生成的帮助类中的所有代码
                String helperClassName = typeElement.getQualifiedName() + "$$Autobind"; //构建要生成的帮助类的类名

                //输出帮助类的java文件，在这个例子中就是MainActivity$$Autobind.java文件
                //输出的文件在build->source->apt->目录下
                try {
                    JavaFileObject jfo = mFilerUtils.createSourceFile(helperClassName, typeElement);
                    Writer writer = jfo.openWriter();
                    writer.write(code);
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            return true;
        }
        return false;
    }
```
process方法的大概流程是：
1. 扫描所有被@BindView标记的Element
2. 遍历Element，调用categories方法，把所有需要绑定的View变量按所在的Activity进行分类，把对应关系存在mToBindMap中
3. 遍历所有Activity的TypeElment，调用generateCode方法获得要生成的代码，每个Activity生成一个帮助类

#### 2-6 categories方法
把所有需要绑定的View变量按所在的Activity进行分类，把对应关系存在mToBindMap中
```
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
```
注解应该已经很详细了~   
实现方式仅供参考，读者可以有自己的实现
#### 2-7 generateCode方法
按照不同的TypeElement生成不同的帮助类(注：参数中的TypeElement对应一个Activity)
```
private String generateCode(TypeElement typeElement) {
        String rawClassName = typeElement.getSimpleName().toString(); //获取要绑定的View所在类的名称
        String packageName = ((PackageElement) mElementsUtils.getPackageOf(typeElement)).getQualifiedName().toString(); //获取要绑定的View所在类的包名
        String helperClassName = rawClassName + "$$Autobind";   //要生成的帮助类的名称

        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(packageName).append(";\n");   //构建定义包的代码
        builder.append("import com.example.apt_api.template.IBindHelper;\n\n"); //构建import类的代码

        builder.append("public class ").append(helperClassName).append(" implements ").append("IBindHelper");   //构建定义帮助类的代码
        builder.append(" {\n"); //代码格式，可以忽略
        builder.append("\t@Override\n");    //声明这个方法为重写IBindHelper中的方法
        builder.append("\tpublic void inject(" + "Object" + " target ) {\n");   //构建方法的代码
        for (ViewInfo viewInfo : mToBindMap.get(typeElement)) { //遍历每一个需要绑定的view
            builder.append("\t\t"); //代码格式，可以忽略
            builder.append(rawClassName + " substitute = " + "(" + rawClassName + ")" + "target;\n");    //强制类型转换

            builder.append("\t\t"); //代码格式，可以忽略
            builder.append("substitute." + viewInfo.viewName).append(" = ");    //构建赋值表达式
            builder.append("substitute.findViewById(" + viewInfo.id + ");\n");  //构建赋值表达式
        }
        builder.append("\t}\n");    //代码格式，可以忽略
        builder.append('\n');   //代码格式，可以忽略
        builder.append("}\n");  //代码格式，可以忽略

        return builder.toString();
    }
```
大家可以对比生成的代码
```
package com.example.apt_demo;
import com.example.apt_api.template.IBindHelper;

public class MainActivity$$Autobind implements IBindHelper {
	@Override
	public void inject(Object target ) {
		MainActivity substitute = (MainActivity)target;
		substitute.testTextView = substitute.findViewById(2131165315);
	}

}
```
有没有觉得字符串拼接很麻烦呢，不仅麻烦还容易出错，那么有没有更好的办法呢（留个坑）


同样的~ 这个方法的设计仅供参考啦啦啦~~~

![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/finished.jpg?raw=true)

### 第三步 注册你的APT
这应该是最简单的一步   
这应该是最麻烦的一步

![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/what.jpg?raw=true)

客官别急，往下看就知道了~

注册一个APT需要以下步骤：
1. 需要在 processors 库的 main 目录下新建 resources 资源文件夹；
2. 在 resources文件夹下建立 META-INF/services 目录文件夹；
3. 在 META-INF/services 目录文件夹下创建 javax.annotation.processing.Processor 文件；
4. 在 javax.annotation.processing.Processor 文件写入注解处理器的全称，包括包路径；

![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/example2.jpg?raw=true)

正如我前面所说的~  
简单是因为都是一些固定的步骤  
麻烦也是因为都是一些固定的步骤

### 最后一步 对外提供API
4.1 创建一个Android Library Module，名称叫apt-api,并添加依赖
```
dependencies {
    ...

    api project(':apt-annotation')
}
```
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/androidstudio_version.jpg?raw=true)

4.2 分别创建launcher、template文件夹  
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/example3.jpg?raw=true)

4.3 在template文件夹中创建IBindHelper接口
```
public interface IBindHelper {
    void inject(Object target);
}
```
这个接口主要供APT生成的帮助类实现


4.4在launcher文件夹中创建AutoBind类
```
    public class AutoBind {
        private static AutoBind instance = null;

        public AutoBind() {
        }

        public static AutoBind getInstance() {
            if(instance == null) {
                synchronized (AutoBind.class) {
                    if (instance == null) {
                        instance = new AutoBind();
                    }
                }
            }
            return instance;
        }

        public void inject(Object target) {
            String className = target.getClass().getCanonicalName();
            String helperName = className + "$$Autobind";
            try {
                IBindHelper helper = (IBindHelper) (Class.forName(helperName).getConstructor().newInstance());
                helper.inject(target);
            }   catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
```
这个类是我们的API的入口类，使用了单例模式  
inject方法是最主要的方法，但实现很简单，就是通过**反射**去调用APT生成的帮助类的方法去实现View的自动绑定

### 完成！拉出来遛遛
在app module里添加依赖
```
dependencies {
    ...
    annotationProcessor project(':apt-compiler')
    implementation project(':apt-api')
}
```
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/androidstudio_version.jpg?raw=true)

我们再来修改MainActivity中的代码
```
public class MainActivity extends AppCompatActivity {

    @BindView(value = R.id.test_textview)
    public TextView testTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AutoBind.getInstance().inject(this);
        testTextView.setText("APT 测试");
    }
}
```

大功告成！我们来运行项目试试看
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/result.jpg?raw=true)

TextView已经正确显示了文字，我们的小demo到这里就完成啦~

## 还可以更好
我们的APT还可以变得更简单！
### 痛点
- 生成代码时字符串拼接麻烦且容易出错
- 继承AbstrctProcessor时要重写多个方法
- 注册APT的步骤繁琐

下面我们来逐个击破~
### 使用JavaPoet来替代拼接字符串
JavaPoet是一个用来生成Java代码的框架，对JavaPoet不了解的请移步[官方文档](https://github.com/square/javapoet)  
JavaPoet生成代码的步骤大概是这样（摘自官方文档）：
```
    MethodSpec main = MethodSpec.methodBuilder("main")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .returns(void.class)
    .addParameter(String[].class, "args")
    .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
    .build();

TypeSpec helloWorld = TypeSpec.classBuilder("HelloWorld")
    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
    .addMethod(main)
    .build();

JavaFile javaFile = JavaFile.builder("com.example.helloworld", helloWorld)
    .build();

javaFile.writeTo(System.out);
```
使用JavaPoet来生成代码有很多的优点，不容易出错，可以自动import等等，这里不过多介绍，有兴趣的同学可以自行了解

### 使用注解来代替getSupportedAnnotationTypes()和getSupportedSourceVersion()
```
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("com.example.apt_annotation.BindView")
public class BindViewProcessor extends AbstractProcessor {
    ...
}
```
这是javax.annotation.processing中提供的注解，直接使用即可

### 使用Auto-Service来自动注册APT
这是谷歌官方出品的一个开源库，可以省去注册APT的步骤，只需要一行注释  
先在apt-compiler模块中添加依赖
```
dependencies {
    ...
    
    implementation 'com.google.auto.service:auto-service:1.0-rc2'
}
```
然后添加注释即可
```
@AutoService(Processor.class)
public class BindViewProcessor extends AbstractProcessor {
    ...
}
```

## 总结
APT是一个非常强大而且频繁出现在各种开源库的工具，学习APT不仅可以让我们在阅读开源库源码时游刃有余也可以自己开发注解框架来帮自己写代码~
[本demo地址](https://github.com/LiMubai2017/aptDemo)
![image](https://github.com/LiMubai2017/ImagesByMuBai/blob/master/end.jpg?raw=true)





