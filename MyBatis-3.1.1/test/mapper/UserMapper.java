package mapper;

import domain.User;

public interface UserMapper {
    /**
     * This method was generated by MyBatis Generator. This method corresponds to the database table t_user
     * 
     * @mbggenerated Mon Dec 03 09:24:51 CST 2012
     */
    int deleteByPrimaryKey(Long userId);

    /**
     * This method was generated by MyBatis Generator. This method corresponds to the database table t_user
     * 
     * @mbggenerated Mon Dec 03 09:24:51 CST 2012
     */
    int insert(User record);

    /**
     * This method was generated by MyBatis Generator. This method corresponds to the database table t_user
     * 
     * @mbggenerated Mon Dec 03 09:24:51 CST 2012
     */
    int insertSelective(User record);

    /**
     * This method was generated by MyBatis Generator. This method corresponds to the database table t_user
     * 
     * @mbggenerated Mon Dec 03 09:24:51 CST 2012
     */
    User selectByPrimaryKey(Long userId);

    /**
     * This method was generated by MyBatis Generator. This method corresponds to the database table t_user
     * 
     * @mbggenerated Mon Dec 03 09:24:51 CST 2012
     */
    int updateByPrimaryKeySelective(User record);

    /**
     * This method was generated by MyBatis Generator. This method corresponds to the database table t_user
     * 
     * @mbggenerated Mon Dec 03 09:24:51 CST 2012
     */
    int updateByPrimaryKey(User record);

}