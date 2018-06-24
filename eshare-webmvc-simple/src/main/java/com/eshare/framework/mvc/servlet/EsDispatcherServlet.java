package com.eshare.framework.mvc.servlet;

import com.eshare.framework.mvc.annotation.*;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分发程序
 * Created by liangyh on 2018/6/20.
 * Email:10856214@163.com
 */
public class EsDispatcherServlet extends HttpServlet {
    /**
     * 配置
     */
    private Properties configProperties = new Properties();
    /**
     * 需要加载的类列表
     */
    private List<String> classNames = new ArrayList<String>();
    /**
     * 类全限定名与实例映射集合
     */
    private Map<String, Object> instanceMapping = new ConcurrentHashMap<String, Object>();
    /**
     * 处理器映射列表
     */
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            //1.读取配置
            loadConfig(config);
            String packageName = configProperties.getProperty("package-scan");
            //2.扫描指定包下的类
            doScanClass(packageName);
            //3.对扫描出来的类实例化
            doInitializeInstance();
            //4.执行类依赖自动注入
            doAutowired();
            //5.配置url和handler映射关系handlerMapping
            doHandlerMapping();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        boolean isMatch = doRequestPattern(req, resp);
        if (!isMatch) {
            resp.getWriter().write("404 Page Not Found !");
        }

    }

    /**
     * 加载配置
     *
     * @param config
     */
    private void loadConfig(ServletConfig config) throws IOException {
        String configFilePath = config.getInitParameter("contextConfigLocation");
        String configName = configFilePath.replace("classpath*:", "");
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(configName);
        configProperties.load(in);
    }

    /**
     * 处理请求映射
     *
     * @param req
     * @param resp
     * @return
     */
    private boolean doRequestPattern(HttpServletRequest req, HttpServletResponse resp) {

        if (handlerMapping.isEmpty()) {
            return false;
        }
        try {
            String url = req.getRequestURI();
            String contextPath = req.getContextPath();
            //去掉Url中的上下文，保留请求资源路径
            url = url.replace(contextPath, "").replaceAll("/+", "/");
            //遍历handlerMapping，查找匹配的handler去处理
            for (Handler handler : handlerMapping) {
                Matcher matcher = handler.getPattern().matcher(url);
                if (!matcher.matches()) {
                    continue;
                }
                Method targetMethod = handler.getMethod();
                //获取方法参数类型
                Class<?>[] paramTypes = targetMethod.getParameterTypes();
                Object[] targetParamsValues = new Object[paramTypes.length];

                //对于用户自定义参数，从请求参数获取用户传参
                Map<String, String[]> paramMap = req.getParameterMap();
                for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                    //转换数组格式为字符串，去掉"[]"，注意存在多个参数时要把单词变为“,”分割
                    String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]", "").replaceAll("\\s", ",");
                    //假如参数集合不包含当前请求参数，直接跳过
                    if (!handler.getParamMapping().containsKey(entry.getKey())) {
                        continue;
                    }
                    //取出当前传入参数在方法里的索引位置
                    Integer index = handler.getParamMapping().get(entry.getKey());
                    //进行类型转换并复制
                    targetParamsValues[index] = castStringToTargetType(value, paramTypes[index]);
                }
                //对于非用户自定义参数,如容器自带request和response,获取它们在集合中的索引，直接注入
                Integer reqIndex = handler.getParamMapping().get(HttpServletRequest.class.getName());
                targetParamsValues[reqIndex] = req;
                Integer respIndex = handler.getParamMapping().get(HttpServletResponse.class.getName());
                targetParamsValues[respIndex] = resp;
                resp.getWriter().write((String) targetMethod.invoke(handler.getController(), targetParamsValues));
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private Object castStringToTargetType(String value, Class<?> paramType) {

        if (paramType == String.class) {
            return value;
        } else if (paramType == Integer.class) {
            return Integer.valueOf(value);
        } else if (paramType == int.class) {
            return Integer.valueOf(value).intValue();
        } else if (paramType == double.class) {
            return Double.valueOf(value).doubleValue();
        } else if (paramType == Double.class) {
            return Double.valueOf(value);
        } else {
            return null;
        }

    }

    /**
     * 执行处理类映射
     */
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
                for (int i = 0; i < types.length; i++) {
                    Class<?> type = types[i];
                    if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                        String paramName = type.getName();
                        paramMapping.put(paramName, i);
                    }
                }
                //创建handler
                handlerMapping.add(new Handler(Pattern.compile(reqRegex), entry.getValue(), m, paramMapping));
                System.out.println("Mapping " + reqRegex + " " + m);
            }
        }

    }

    private void doAutowired() {
        if (instanceMapping.isEmpty()) {
            return;
        }

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


    class Handler {
        private Pattern pattern;
        private Object controller;
        private Method method;
        private Map<String, Integer> paramMapping;


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
