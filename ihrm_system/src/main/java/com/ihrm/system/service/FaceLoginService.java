package com.ihrm.system.service;


import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baidu.aip.util.Base64Util;
import com.ihrm.common.utils.IdWorker;
import com.ihrm.domain.system.User;
import com.ihrm.domain.system.response.FaceLoginResult;
import com.ihrm.domain.system.response.QRCode;
import com.ihrm.system.dao.UserDao;
import com.ihrm.system.utils.BaiduAiUtil;
import com.ihrm.system.utils.QRCodeUtil;

@Service
public class FaceLoginService {

    @Value("${qr.url}")
    private String url;
    
    @Autowired
    private IdWorker idWorker;
    
    @Autowired
    private QRCodeUtil qRCodeUtil;
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    BaiduAiUtil baiduAiUtil;

	//创建二维码
    public QRCode getQRCode() throws Exception {
    	//1.创建唯一标识
    	String code = idWorker.nextId() + "";
    	//2.生成二维码(url地址)
    	String content = url + "?code="+code;
    	String file = qRCodeUtil.crateQRCode(content);
    	//System.out.println(file);
    	//3.存入当前二维码状态到redis
    	FaceLoginResult result = new FaceLoginResult("-1");
    	redisTemplate.boundValueOps(getCacheKey(code)).set(result, 10, TimeUnit.MINUTES);//状态对象，失效时间，单位
		return new QRCode(code,file);
    }

	//根据唯一标识，查询用户是否登录成功
    public FaceLoginResult checkQRCode(String code) {
    	String key = getCacheKey(code);
    	return (FaceLoginResult) redisTemplate.opsForValue().get(key);
    }
    
    @Autowired
    private UserDao userDao;

	//扫描二维码之后，使用拍摄照片进行登录
    //返回值：登录成功之后返回的用户id
    //登录失败，返回null
    public String loginByFace(String code, MultipartFile attachment) throws Exception {
    	//1.调用百度云AI搜索图片用户
    	String image = Base64Util.encode(attachment.getBytes());
    	String userId = baiduAiUtil.faceSearch(image);
    	//2.自动登录（token）
    	FaceLoginResult result = new FaceLoginResult("0");
    	if(userId!=null) {
    		//自己模拟登录
    		//查询用户对象
    		User user = userDao.findById(userId).get();
    		//获取subject
    		if(user!=null) {
    			Subject subject = SecurityUtils.getSubject();
    			//调用login方法登录
        		subject.login(new UsernamePasswordToken(user.getMobile(), user.getPassword()));
        		//获取token（sessionId）
        		String token = subject.getSession().getId() + "";
        		result = new FaceLoginResult("1",token,userId);
    		}
    		
    	}
    	//3.修改二维码的状态并存入token
    	redisTemplate.boundValueOps(getCacheKey(code)).set(result,10,TimeUnit.MINUTES);
		return userId;
    }

	//构造缓存key
    private String getCacheKey(String code) {
        return "qrcode_" + code;
    }
}
