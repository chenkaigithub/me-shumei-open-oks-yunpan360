package me.shumei.open.oks.yunpan360;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
    String resultFlag = "false";
    String resultStr = "未知错误！";
    
    String user;
    String pwd;
    private String loginQid;//360的qid号
    private boolean isLoginSucceed = false;//登录是否成功
    
    
    /**
     * <p><b>程序的签到入口</b></p>
     * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
     * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
     * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
     * @param cfg “配置”栏内输入的数据
     * @param user 用户名
     * @param pwd 解密后的明文密码
     * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
     */
    public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
        //把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
        CaptchaUtil.context = ctx;
        //标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
        CaptchaUtil.isAutoSign = isAutoSign;
        
        try{
            //存放Cookies的HashMap
            HashMap<String, String> cookies = new HashMap<String, String>();
            //Jsoup的Response
            Response res;
            
            String realTokenUrl = "http://yunpan.360.cn/user/login?st=" + (int)(Math.random() * 100);//获取网盘token的链接
            String signinUrl = null;//签到的链接，前面的集群服务器会变动，类似http://c17.yunpan.360.cn/user/signin/前的“c17”
            
            //调用360通行证登录函数
            this.user = user;
            this.pwd = pwd;
            cookies = login360();
            if(isLoginSucceed)
            {
                //构造360云盘的登录Cookies
                String user_encoded = URLEncoder.encode(user, "UTF-8");;
                //cookies.put("YUNPAN_USER", URLEncoder.encode(user_encoded, "UTF-8"));
                cookies.put("YUNPAN_USER", user_encoded);
                
                //获取网盘的token
                res = Jsoup.connect(realTokenUrl).cookies(cookies).userAgent(UA_CHROME).referrer(realTokenUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
                cookies.putAll(res.cookies());
                signinUrl = "http://" + res.url().getHost() + "/user/signin/";
                
                //以上正式完成登录，获取完所有需要的cookies数据，最后要做的就是签到了
                res = Jsoup.connect(signinUrl).data("qid", loginQid).data("method", "signin").cookies(cookies).userAgent(UA_CHROME).referrer(signinUrl).timeout(TIME_OUT).method(Method.POST).ignoreContentType(true).execute();
                JSONObject signinObj = new JSONObject(res.body());
                JSONObject dataObj = signinObj.getJSONObject("data");
                int errno = signinObj.getInt("errno");
                if(errno == 0)
                {
                    int reward = dataObj.getInt("reward") / 1048576;
                    resultFlag = "true";
                    resultStr = "签到成功，领取了" + reward + "MB空间";
                }
                else if(errno == 27002)
                {
                    int total = dataObj.getInt("total") / 1048576;
                    resultFlag = "true";
                    resultStr = "今日已签过到，领取了" + total + "MB空间";
                }
                else
                {
                    resultFlag = "false";
                    resultStr = "登录成功，签到失败！";
                }
            }
            
            
        } catch (IOException e) {
            this.resultFlag = "false";
            this.resultStr = "连接超时";
            e.printStackTrace();
        } catch (Exception e) {
            this.resultFlag = "false";
            this.resultStr = "未知错误！";
            e.printStackTrace();
        }
        
        return new String[]{resultFlag, resultStr};
    }
    
    
    
    /**
     * 登录360通行证
     * http://i.360.cn/login/
     * 登录步骤
     * 1.手动构造两个Cookies：i360loginName=shumei%2540gmail.com，trad=0
     * 2.用构造出的Cookies访问获取全局token的链接：http://login.360.cn/?o=sso&m=getToken&func=QHPass.loginUtils.tokenCallback&userName=shumei%40gmail.com&rand=0.657428435748443&callback=QiUserJsonP1350462351234
     * 3.提取出token值，加入到链接中：http://login.360.cn/?o=sso&m=login&from=i360&rtype=data&func=QHPass.loginUtils.loginCallback&userName=shumei%40gmail.com&password=c94210819524e90f3cdd65fd1786dddd&isKeepAlive=0&token=ca925dc9dd1a5123&captFlag=&r=
     * userName是用户名经过encodeURIComponent编码的字符串
     * password是经过32位md5加密的密码
     * token是第2步中获取出的token值
     * @return HashMap<String, String>
     */
    public HashMap<String, String> login360(){
        String user_encoded;
        String pwd_encrypted;
        HashMap<String, String> cookies = new HashMap<String, String>();
        
        try {
            //编码用户名，32位md5加密密码
            user_encoded = URLEncoder.encode(user, "UTF-8");
            pwd_encrypted = MD5.md5(pwd);
            
            Response res;
            String golbalTokenUrl = "http://login.360.cn/?o=sso&m=getToken&func=QHPass.loginUtils.tokenCallback&userName=" + user_encoded;//获取全局token的URL
            String loginUrl;//用全局token登录账号的URL
            cookies.put("i360loginName", user_encoded);
            cookies.put("trad", "0");
            
            try {
                //先用账号获取临时token字符串
                res = Jsoup.connect(golbalTokenUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).method(Method.GET).ignoreContentType(true).execute();
                
                //提取临时token
                //{"errno":0,"errmsg":"","token":"f6123404fdb1a222"}
                String tokenCallbackStr = res.body().replace("QHPass.loginUtils.tokenCallback(", "").replace("})", "}");
                String token = new JSONObject(tokenCallbackStr).getString("token");
                
                //使用全局token登录网站，获取名为T和Q的cookie
                loginUrl = "http://login.360.cn/?o=sso&m=login&from=i360&rtype=data&func=QHPass.loginUtils.loginCallback&isKeepAlive=0&captFlag=&r=&" +
                        "&userName=" + user_encoded +
                        "&password=" + pwd_encrypted +
                        "&token=" + token;
                res = Jsoup.connect(loginUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
                cookies.putAll(res.cookies());
                
                //{"errno":0,"errmsg":"","s":"e27V.%60togp%3F%2BeADa","userinfo":{"qid":"154601234","userName":"360U154601234","nickName":"","realName":"","imageId":"190144aq111234","theme":"360","src":"yunpan","type":"formal","loginEmail":"shumei@shumei.me","loginTime":"1350483069","isKeepAlive":"0","crumb":"af446e","imageUrl":"http:\/\/u1.qhimg.com\/qhimg\/quc\/48_48\/22\/02\/55\/220255dq1234.3eceac.jpg"}}
                //{"errno":220,"errmsg":"\u767b\u5f55\u5bc6\u7801\u9519\u8bef\uff0c\u8bf7\u91cd\u65b0\u8f93\u5165"}
                //{"errno":1036,"errmsg":"\u5e10\u53f7\u4e0d\u5b58\u5728"}
                String loginCallbackStr = res.parse().text().replace("QHPass.loginUtils.loginCallback(", "").replace("})", "}");
                System.out.println(loginCallbackStr);
                JSONObject callBackObj = new JSONObject(loginCallbackStr);
                int errno = callBackObj.getInt("errno");
                if(errno == 0)
                {
                    String qid = callBackObj.getJSONObject("userinfo").getString("qid");
                    loginQid = qid;//保存qid给后续操作用
                    isLoginSucceed = true;//登录成功
                }
                else if(errno == 220)
                {
                    resultFlag = "false";
                    resultStr = "密码错误";
                }
                else if(errno == 1060)
                {
                    resultFlag = "false";
                    resultStr = "密码不合法";
                }
                else if(errno == 8201)
                {
                    resultFlag = "false";
                    resultStr = "无效的登录";
                }
                else if(errno == 1036)
                {
                    resultFlag = "false";
                    resultStr = "账号不存在";
                }
                else
                {
                    resultFlag = "false";
                    resultStr = "已签过到";
                }
            } catch (JSONException e) {
                resultFlag = "false";
                resultStr = "登录失败";
                e.printStackTrace();
            }
        } catch (IOException e) {
            this.resultFlag = "false";
            this.resultStr = "连接超时";
            e.printStackTrace();
        }
        
        return cookies;
    }
    
    
}
