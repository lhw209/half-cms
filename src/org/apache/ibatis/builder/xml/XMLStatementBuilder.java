/*
 *    Copyright 2009-2012 The MyBatis Team
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.dynamic.ChooseSqlNode;
import org.apache.ibatis.builder.xml.dynamic.DynamicSqlSource;
import org.apache.ibatis.builder.xml.dynamic.ForEachSqlNode;
import org.apache.ibatis.builder.xml.dynamic.IfSqlNode;
import org.apache.ibatis.builder.xml.dynamic.MixedSqlNode;
import org.apache.ibatis.builder.xml.dynamic.SetSqlNode;
import org.apache.ibatis.builder.xml.dynamic.SqlNode;
import org.apache.ibatis.builder.xml.dynamic.TextSqlNode;
import org.apache.ibatis.builder.xml.dynamic.TrimSqlNode;
import org.apache.ibatis.builder.xml.dynamic.WhereSqlNode;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLStatementBuilder extends BaseBuilder {

  private MapperBuilderAssistant builderAssistant;
  private XNode context;
  private String requiredDatabaseId;

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    super(configuration);
    this.builderAssistant = builderAssistant;
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }

  public void parseStatementNode() {
    String id = context.getStringAttribute("id");
    String databaseId = context.getStringAttribute("databaseId");

    if (!databaseIdMatchesCurrent(id, databaseId)) return;

    Integer fetchSize = context.getIntAttribute("fetchSize", null);
    Integer timeout = context.getIntAttribute("timeout", null);
    String parameterMap = context.getStringAttribute("parameterMap");
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);
    String resultMap = context.getStringAttribute("resultMap");
    String resultType = context.getStringAttribute("resultType");

    Class<?> resultTypeClass = resolveClass(resultType);
    String resultSetType = context.getStringAttribute("resultSetType");
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    List<SqlNode> contents = parseDynamicTags(context);
    // Parse selectKey after other nodes,
    // in case another node throws IncompleteElementException (issue #291)
    parseSelectKey(context);
    MixedSqlNode rootSqlNode = new MixedSqlNode(contents);
    SqlSource sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    String nodeName = context.getNode().getNodeName();
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);

    String keyProperty = context.getStringAttribute("keyProperty");
    String keyColumn = context.getStringAttribute("keyColumn");
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
    }

    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, keyGenerator, keyProperty, keyColumn, databaseId);
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this statement if there is a previous one with a not null databaseId
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (this.configuration.hasStatement(id, false)) {
        MappedStatement previous = this.configuration.getMappedStatement(id);
        if (previous.getDatabaseId() != null) {
          return false;
        }
      }
    }
    return true;
  }
  
  private List<SqlNode> parseDynamicTags(XNode node) {
    List<SqlNode> contents = new ArrayList<SqlNode>();
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      XNode child = node.newXNode(children.item(i));
      String nodeName = child.getNode().getNodeName();
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE
          || child.getNode().getNodeType() == Node.TEXT_NODE) {
        String data = child.getStringBody("");
        contents.add(new TextSqlNode(data));
      } else if (!"selectKey".equals(nodeName)) {
        NodeHandler handler = nodeHandlers.get(nodeName);
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        handler.handleNode(child, contents);
      }
    }
    return contents;
  }

  private void parseSelectKey(XNode node) {
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      XNode child = node.newXNode(children.item(i));
      String nodeName = child.getNode().getNodeName();
      if ("selectKey".equals(nodeName)) {
        NodeHandler handler = nodeHandlers.get(nodeName);
        handler.handleNode(child, null);
      }
    }
  }

  private Map<String, NodeHandler> nodeHandlers = new HashMap<String, NodeHandler>() {
    private static final long serialVersionUID = 7123056019193266281L;

    {
      put("include", new IncludeNodeHandler());
      put("trim", new TrimHandler());
      put("where", new WhereHandler());
      put("set", new SetHandler());
      put("foreach", new ForEachHandler());
      put("if", new IfHandler());
      put("choose", new ChooseHandler());
      put("when", new IfHandler());
      put("otherwise", new OtherwiseHandler());
      put("selectKey", new SelectKeyHandler());
    }
  };

  private interface NodeHandler {
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }

  private class SelectKeyHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      XNode parent = nodeToHandle.getParent();
      String id = parent.getStringAttribute("id") + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      String resultType = nodeToHandle.getStringAttribute("resultType");
      Class<?> resultTypeClass = resolveClass(resultType);
      StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
      String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
      String parameterType = parent.getStringAttribute("parameterType");
      String databaseId = parent.getStringAttribute("databaseId");
      boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));
      Class<?> parameterTypeClass = resolveClass(parameterType);

      //defaults
      boolean useCache = false;
      KeyGenerator keyGenerator = new NoKeyGenerator();
      Integer fetchSize = null;
      Integer timeout = null;
      boolean flushCache = false;
      String parameterMap = null;
      String resultMap = null;
      ResultSetType resultSetTypeEnum = null;

      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode rootSqlNode = new MixedSqlNode(contents);
      SqlSource sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
      SqlCommandType sqlCommandType = SqlCommandType.SELECT;

      builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
          fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
          resultSetTypeEnum, flushCache, useCache, keyGenerator, keyProperty, null, databaseId);

      id = builderAssistant.applyCurrentNamespace(id, false);

      MappedStatement keyStatement = configuration.getMappedStatement(id, false);
      configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
    }
  }

  private class IncludeNodeHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      String refid = nodeToHandle.getStringAttribute("refid");
      refid = builderAssistant.applyCurrentNamespace(refid, true);
      try {
        XNode includeNode = configuration.getSqlFragments().get(refid);
        if (includeNode == null) {
          String nsrefid = builderAssistant.applyCurrentNamespace(refid, true);
          includeNode = configuration.getSqlFragments().get(nsrefid);
          if (includeNode == null) {
            throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'");
          }
        }
        MixedSqlNode mixedSqlNode = new MixedSqlNode(contents(includeNode));
        targetContents.add(mixedSqlNode);
      } catch (IllegalArgumentException e) {
        throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
      }
    }

    private List<SqlNode> contents(XNode includeNode) {
      return parseDynamicTags(includeNode);
    }
  }

  private class TrimHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      String prefix = nodeToHandle.getStringAttribute("prefix");
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      String suffix = nodeToHandle.getStringAttribute("suffix");
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      targetContents.add(trim);
    }
  }

  private class WhereHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      targetContents.add(where);
    }
  }

  private class SetHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      targetContents.add(set);
    }
  }

  private class ForEachHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      String collection = nodeToHandle.getStringAttribute("collection");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
      targetContents.add(forEachSqlNode);
    }
  }

  private class IfHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      String test = nodeToHandle.getStringAttribute("test");
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      targetContents.add(ifSqlNode);
    }
  }

  private class OtherwiseHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      targetContents.add(mixedSqlNode);
    }
  }

  private class ChooseHandler implements NodeHandler {
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List whenSqlNodes = new ArrayList<SqlNode>();
      List<SqlNode> otherwiseSqlNodes = new ArrayList<SqlNode>();
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode((List<IfSqlNode>) whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
      List<XNode> children = chooseSqlNode.getChildren();
      for (XNode child : children) {
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlers.get(nodeName);
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
