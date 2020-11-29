package wang.wind.rn.codeupdate;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * 公众平台通用接口工具类 
 * 用户实现https请求
 * @author zhangwenchao
 *
 */
public class HttpsConnection {

    /**
     * 发起https请求并获取结果 
     *
     * @param requestUrl 请求地址 
     * @param requestMethod 请求方式（GET、POST） 
     * @param outputStr 提交的数据 
     * @return JSONObject(通过JSONObject.get(key)的方式获取json对象的属性值) 
     */
    public static JSONObject request(String requestUrl, String requestMethod, String outputStr) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, KeyManagementException, JSONException {
        JSONObject jsonObject = null;
        StringBuffer buffer = new StringBuffer();
        try {

            // 创建SSLContext对象，并使用我们指定的信任管理器初始化   
            TrustManager[] tm = { new MyX509TrustManager() };
            SSLContext sslContext = SSLContext.getInstance("SSL", "SunJSSE");
            sslContext.init(null, tm, new SecureRandom());

            // 从上述SSLContext对象中得到SSLSocketFactory对象   
            SSLSocketFactory ssf = sslContext.getSocketFactory();

            //打开连接
            URL url = new URL(requestUrl);
            HttpsURLConnection httpsUrlConn = (HttpsURLConnection) url.openConnection();
            httpsUrlConn.setSSLSocketFactory(ssf);
            httpsUrlConn.setDoOutput(true);
            httpsUrlConn.setDoInput(true);
            httpsUrlConn.setUseCaches(false);

            // 设置请求方式（GET/POST）   
            httpsUrlConn.setRequestMethod(requestMethod);

            if ("GET".equalsIgnoreCase(requestMethod)){
                httpsUrlConn.connect();
            }
            // 当有数据需要提交时   
            if (null != outputStr) {
                OutputStream outputStream = httpsUrlConn.getOutputStream();
                // 注意编码格式，防止中文乱码   
                outputStream.write(outputStr.getBytes("UTF-8"));
                outputStream.close();
            }

            // 将返回的输入流转换成字符串   
            InputStream inputStream = httpsUrlConn.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String str = null;
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputStreamReader.close();
            // 释放资源   
            inputStream.close();
            httpsUrlConn.disconnect();
            jsonObject = new JSONObject(buffer.toString());
        } catch (ConnectException ce) {
            throw ce;
        } catch (Exception e) {
            throw e;
        }
        return jsonObject;
    }

}