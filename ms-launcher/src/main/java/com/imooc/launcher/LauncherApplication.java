package com.imooc.launcher;

import com.imooc.registry.RegistryApplication;
import com.imooc.oauth2.server.Oauth2ServerApplication;
import com.imooc.diners.DinersApplication;
import com.imooc.seckill.SeckillApplication;
import com.imooc.gateway.GatewayApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class LauncherApplication {

    public static void main(String[] args) {
        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(5);

        // 提交各个服务启动任务
        executor.submit(() -> new SpringApplicationBuilder(RegistryApplication.class).run(args));
        executor.submit(() -> new SpringApplicationBuilder(Oauth2ServerApplication.class).run(args));
        executor.submit(() -> new SpringApplicationBuilder(DinersApplication.class).run(args));
        executor.submit(() -> new SpringApplicationBuilder(SeckillApplication.class).run(args));
        executor.submit(() -> new SpringApplicationBuilder(GatewayApplication.class).run(args));

        // 关闭线程池
        executor.shutdown();
    }
}

