package com.fc.v2;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.TimeZone;

/**
 * mgis 业务层集成测试的最小持久化基座：H2（MODE=MySQL）+ 手工装配 MyBatis-Plus，
 * 不启动 Spring/Shiro/Druid 整站。每个用例拿到一个独立内存库。
 */
public final class MgisDb {

    public final DataSource dataSource;
    public final SqlSessionFactory factory;
    public final SqlSession session;

    private MgisDb(DataSource dataSource, SqlSessionFactory factory, SqlSession session) {
        this.dataSource = dataSource;
        this.factory = factory;
        this.session = session;
    }
    public static MgisDb start() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"));
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:mgis_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c, new org.springframework.core.io.FileSystemResource("src/test/resources/h2/schema-mgis.sql"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        // 不依赖 Shiro 上下文：填充一个稳定的登录名，便于断言 createBy/updateBy。
        globalConfig.setMetaObjectHandler(new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createBy", String.class, "建档员甲");
                this.strictInsertFill(metaObject, "createTime", java.util.Date.class, new java.util.Date());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateBy", String.class, "建档员甲");
                this.strictUpdateFill(metaObject, "updateTime", java.util.Date.class, new java.util.Date());
            }
        });

        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(ds);
        bean.setConfiguration(configuration);
        bean.setGlobalConfig(globalConfig);
        try {
            SqlSessionFactory f = bean.getObject();
            // SqlSessionTemplate：自动提交，且把底层异常按 MyBatis-Spring 口径转换
            // （唯一键冲突 -> DuplicateKeyException），与生产环境一致。
            SqlSession s = new SqlSessionTemplate(f);
            return new MgisDb(ds, f, s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T mapper(Class<T> clazz) {
        // Configuration 里注册的是 Mapper 接口；factory.getConfiguration 已由 MP 扫描注解实体
        if (factory.getConfiguration().hasMapper(clazz)) {
            return session.getMapper(clazz);
        }
        factory.getConfiguration().addMapper(clazz);
        return session.getMapper(clazz);
    }

    public void close() {
        // SqlSessionTemplate 由 Spring 管理，禁止手动关闭；
        // 每个用例使用独立内存库，随 JVM 回收即可。
    }

    public static Timestamp ts(String ymdhms) {
        return Timestamp.valueOf(ymdhms);
    }

    public static Date date(String ymd) {
        return Date.valueOf(ymd);
    }
}
