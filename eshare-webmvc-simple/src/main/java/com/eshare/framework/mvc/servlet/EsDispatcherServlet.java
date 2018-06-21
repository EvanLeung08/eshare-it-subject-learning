package com.eshare.framework.mvc.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 分发程序
 * Created by liangyh on 2018/6/20.
 * Email:10856214@163.com
 */
public class EsDispatcherServlet extends HttpServlet {

    private Properties configProperties = new Properties();
    private List<String> classNames = new ArrayList<String>();
    @Override
    public void init(ServletConfig config) throws ServletException {
        //读取配置
        String configFilePath = config.getInitParameter("contextConfigLocation");
        String configName = configFilePath.replace("classpath*:", "");
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(configName);
        String packageName;
        try {
            configProperties.load(in);
            packageName = configProperties.getProperty("package-scan");
            doScanClass(packageName);

        } catch (IOException e) {
            e.printStackTrace();
        }
        super.init(config);
    }
    private void doScanClass(String packageName){
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File file = new File(url.getFile());
        for(File f:file.listFiles()){
            if(f.isDirectory()){
                doScanClass(packageName+"."+f.getName());
            }else{
               String className= packageName+"."+f.getName().replace(".class","");
               //存放到类名集合
                classNames.add(className);
                System.out.println(className);
            }
        }
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }
}
