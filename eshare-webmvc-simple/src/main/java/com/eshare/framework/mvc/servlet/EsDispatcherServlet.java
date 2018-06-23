package com.eshare.framework.mvc.servlet;

import com.eshare.demo.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 分发程序
 * Created by liangyh on 2018/6/20.
 * Email:10856214@163.com
 */
public class EsDispatcherServlet extends HttpServlet {

    private Properties configProperties = new Properties();
    private List<String> classNames = new ArrayList<String>();
    private Map<String, Object> instanceMapping = new ConcurrentHashMap<String, Object>();

    List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.读取配置
        String configFilePath = config.getInitParameter("contextConfigLocation");
        String configName = configFilePath.replace("classpath*:", "");
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(configName);
        String packageName;
        try {
            configProperties.load(in);
            packageName = configProperties.getProperty("package-scan");
            //2.扫描指定包下的类
            doScanClass(packageName);
            //3.对扫描出来的类实例化
            doInitializeInstance();
            //4.自动注入
            doAutowired();
            //5.配置url和handler映射关系handlerMapping
            doHandlerMapping();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doHandlerMapping() {
        if (instanceMapping.isEmpty()) return;
        for (Map.Entry entry : instanceMapping.entrySet()) {
            //先获取controller上的requestMapping值
            Class<?> clazz = entry.getValue().getClass();
            String baseUrl = "";
            //检查是否为controller
            if (clazz.isAnnotationPresent(EsController.class)) {
                if (clazz.isAnnotationPresent(EsRequestMapping.class)) {
                    EsRequestMapping esRequestMapping = clazz.getAnnotation(EsRequestMapping.class);
                    baseUrl = esRequestMapping.value();
                }
            }
            //获取当前类的所有方法
            Method[] method = clazz.getMethods();

            for (Method m : method) {


                //方法上是否存在EsRequestMapping注解
                if (!m.isAnnotationPresent(EsRequestMapping.class)) {
                    continue;
                }

                EsRequestMapping esRequestMapping = m.getAnnotation(EsRequestMapping.class);
                String customRegex = ("/" + baseUrl + esRequestMapping.value()).replaceAll("/+", "/");
                String reqRegex = customRegex.replaceAll("\\*", ".*");

                Map<String, Integer> paramMapping = new HashMap<String, Integer>();

                //获取方法中的参数，对有自定义注解的参数将其名称和顺序映射放到集合中
                Annotation[][] paramAnnotations = m.getParameterAnnotations();

                for (int i = 0; i < paramAnnotations.length; i++) {
                    for (Annotation a : paramAnnotations[i]) {
                        //如果注解是EsRequestParam类型
                        if (a instanceof EsRequestParam) {
                            String paramName = ((EsRequestParam) a).value();
                            if (!"".equals(paramName)) {
                                paramMapping.put(paramName, i);
                            }
                        }
                    }
                }
                //处理非自定义注解参数，request,response
                Class<?>[] types = m.getParameterTypes();
                for (int i=0;i<types.length;i++) {
                       Class<?> type = types[i];
                    if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                        String paramName = type.getName();
                        paramMapping.put(paramName,i);
                    }
                }
                //创建handler
                handlerMapping.add(new Handler(Pattern.compile(reqRegex),entry.getValue(),m,paramMapping));
                System.out.println("Mapping "+reqRegex+" "+m);
            }
        }

    }

    private void doAutowired() {
        if (instanceMapping.isEmpty()) return;

        for (Map.Entry<String, Object> entry : instanceMapping.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                //暴力破解权限
                field.setAccessible(true);
                //检查是否带有@Autowired注解
                if (field.isAnnotationPresent(EsAutowired.class)) {
                    EsAutowired esAutowired = field.getAnnotation(EsAutowired.class);
                    String beanName = esAutowired.value().trim();
                    if ("".equals(beanName)) {
                        beanName = field.getType().getName();
                    }
                    try {
                        field.set(entry.getValue(), instanceMapping.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        continue;
                    }

                }
            }
        }

    }

    private void doScanClass(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File file = new File(url.getFile());
        for (File f : file.listFiles()) {
            if (f.isDirectory()) {
                doScanClass(packageName + "." + f.getName());
            } else {
                String className = packageName + "." + f.getName().replace(".class", "");
                //存放到类名集合
                classNames.add(className);
            }
        }
    }

    private void doInitializeInstance() {
        //如果不存在要加载的类,则直接返回
        if (classNames.isEmpty()) {
            return;
        }

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                //查看当前类字节码是否存在EsController、EsService注解
                if (clazz.isAnnotationPresent(EsController.class)) {
                    String beanName = lowerFirstChar(className);
                    instanceMapping.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(EsService.class)) {
                    EsService esService = clazz.getAnnotation(EsService.class);
                    //检查是否存在自定义名称
                    String beanName = esService.value().trim();
                    if (!"".equals(beanName.trim())) {
                        instanceMapping.put(beanName, clazz.newInstance());
                        continue;
                    }
                    //没有自定义名称，则以接口名称作为key
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class i : interfaces) {
                        instanceMapping.put(i.getName(), clazz.newInstance());
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String lowerFirstChar(String className) {
        char[] chars = className.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);

    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }


    class Handler {
        private Pattern pattern;
        private Object controller;
        private Method method;
        private Map<String, Integer> paramMapping = new ConcurrentHashMap<String, Integer>();


        public Handler(Pattern pattern, Object controller, Method method, Map<String, Integer> paramMapping) {
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;
            this.paramMapping = paramMapping;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public void setPattern(Pattern pattern) {
            this.pattern = pattern;
        }

        public Object getController() {
            return controller;
        }

        public void setController(Object controller) {
            this.controller = controller;
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            this.method = method;
        }

        public Map<String, Integer> getParamMapping() {
            return paramMapping;
        }

        public void setParamMapping(Map<String, Integer> paramMapping) {
            this.paramMapping = paramMapping;
        }
    }
}
