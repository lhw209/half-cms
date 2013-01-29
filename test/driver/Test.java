package driver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.log4j.Logger;

public class Test {
    private static Logger log = Logger.getLogger(Test.class);

    public static void main(String args[]) throws IOException {
        org.apache.ibatis.logging.LogFactory.useLog4JLogging();

        String resource = "mybatis-config.xml";
        InputStream reader = Resources.getResourceAsStream(resource);
        Properties prop = new Properties();
        prop.load(Resources.getResourceAsReader("config.properties"));
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader, prop);

        SqlSession session = sqlSessionFactory.openSession();
        session.selectOne("mapper.UserMapper.selectByPrimaryKey", 101);
        log.debug("over");
    }
}
