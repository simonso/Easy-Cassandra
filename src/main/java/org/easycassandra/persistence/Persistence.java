/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.easycassandra.persistence;

import org.easycassandra.annotations.ColumnValue;


import org.easycassandra.annotations.read.UTF8Read;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.easycassandra.util.EncodingUtil;


import org.easycassandra.util.ReflectionUtil;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.cassandra.thrift.Cassandra.Client;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.Compression;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.CqlResult;
import org.apache.cassandra.thrift.CqlRow;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;

import org.apache.thrift.TException;
import org.easycassandra.ConsistencyLevelCQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *main Class for persistence The Objects
 * @author otaviojava - otaviojava@java.net
 */
public class Persistence extends BasePersistence {

    private static Logger LOOGER = LoggerFactory.getLogger(Persistence.class);
    protected Client client = null;

    Persistence(Client client, AtomicReference<ColumnFamilyIds> referenciaSuperColunas) {
        this.client = client;
        this.referenciaSuperColunas = referenciaSuperColunas;
    }

    /**
     * method for retrieve Objects
     * @param condiction -condiction for search the Objects can be index and rowkey
     * @param condictionValue - value for seach in condiction
     * @param objects - List for the object retrieve 
     * @param persistenceClass - type object class
     * @param consistencyLevel level consistency for retrive the information
     * @param limit length max the list
     * @return the list retrieved with length (limit)
    
     */
    @SuppressWarnings("unchecked")
    protected List retriveObject(String condiction, String condictionValue, List objects, Class persistenceClass, ConsistencyLevelCQL consistencyLevel, int limit) {
        try {
            StringBuilder cql = new StringBuilder();

            cql.append("select KEY, ");

            cql.append(columnNames(persistenceClass));
            cql.append(" from ");
            cql.append(getColumnFamilyName(persistenceClass));
            cql.append(" USING ").append(consistencyLevel.getValue()).append(" ");   //padra One
            cql.append(" where ");
            cql.append(" ").append(condiction).append(" =");
            cql.append("'");
            cql.append(condictionValue);
            cql.append("' ");
            cql.append("LIMIT ").append(limit);//default 10000


            CqlResult execute_cql_query = executeCQL(cql.toString());
            objects = listbyQuery(execute_cql_query, persistenceClass);
        } catch (NumberFormatException | InstantiationException | IllegalAccessException ex) {
            LOOGER.error("Error during execute CQL", ex);

        }

        return objects;
    }

    //insert commands
    /**
     * the method for insert the Object
     * @param object - the  Object for be insert in Cassandra 
     * the default  of consistency Level is ONE (ConsistencyLevel.ONE)
     * @see #insert(java.lang.Object, org.apache.cassandra.thrift.ConsistencyLevel) 
     */
    public void insert(Object object) {
        insert(object, ConsistencyLevel.ONE);
    }

    /**
     * the method for insert the Object
     * @param object - the  Object for be insert in Cassandra 
     * @param consistencyLevel - Level consistency for be insering
     */
    public void insert(Object object, ConsistencyLevel consistencyLevel) {
        try {
            ColumnParent parent = new ColumnParent(getColumnFamilyName(object.getClass()));

            ByteBuffer rowid;

            rowid = getKey(object);

            List<Column> columns = getColumns(object);

            for (Column column : columns) {

                client.insert(rowid, parent, column, consistencyLevel);

            }
        } catch (IOException | InvalidRequestException | UnavailableException | TimedOutException | TException ex) {
            LOOGER.error("Error insert Objects", ex);
        }
    }

    /**
     * @param cql - Cassandra Query Language 
     * @return  the result executing query
     */
    public CqlResult executeCQL(String cql) {
        try {
            return client.execute_cql_query(ByteBuffer.wrap(cql.toString().getBytes()), Compression.NONE);
        } catch (InvalidRequestException | UnavailableException | TimedOutException | SchemaDisagreementException | TException ex) {
            LOOGER.error("Error during execute CQL", ex);
        }
        return null;
    }

    /**
     * the method for retrieve the object in Cassandra
     * @param persistenceClass - type of class for be retrieve
     * the default  of consistency Level is ONE (ConsistencyLevel.ONE)
     * The default lenght list is 10.000
     * @see  #findAll(java.lang.Class, org.easycassandra.ConsistencyLevelCQL, int) 
     * @return the list with Object is retrive
     */
    public List findAll(Class persistenceClass) {
        return findAll(persistenceClass, ConsistencyLevelCQL.ONE, 10000);
    }

    /**
     * the method for retrieve the object in Cassandra
     * @param persistenceClass - type of class for be retrieve
     * @param limit - lenght the list
     * The default  of consistency Level is ONE (ConsistencyLevel.ONE)
     * @see  #findAll(java.lang.Class, org.easycassandra.ConsistencyLevelCQL, int) 
     * @return the list with Object is retrive
     */
    public List findAll(Class persistenceClass, int limit) {
        return findAll(persistenceClass, ConsistencyLevelCQL.ONE, limit);
    }

    /**
     * the method for retrieve the object in Cassandra
     * @param persistenceClass -  type of class for be retrieve
     * @param consistencyLevel - Level of consitency for retrive the Object
     * @see  #findAll(java.lang.Class, org.easycassandra.ConsistencyLevelCQL, int) 
     * @return the list with Object is retrive
     */
    public List findAll(Class persistenceClass, ConsistencyLevelCQL consistencyLevel) {
        return findAll(persistenceClass, consistencyLevel, 10000);
    }

    /**
     * the method for retrieve the object in Cassandra
     * @param persistenceClass -  type of class for be retrieve
     * @param consistencyLevel - Level of consitency for retrive the Object
     * @param limit - lenght the list
     * @return the list with Object is retrive
     */
    public List findAll(Class persistenceClass, ConsistencyLevelCQL consistencyLevel, int limit) {
        List list = new ArrayList<>();

        try {

            StringBuilder cql = new StringBuilder();
            cql.append(" select  KEY, ");
            cql.append(columnNames(persistenceClass));
            cql.append(" from ");
            cql.append(getColumnFamilyName(persistenceClass));
            cql.append(" USING ").append(consistencyLevel.getValue()).append(" ");   //padra One
            cql.append("LIMIT ").append(limit);//padrao 10000
            CqlResult execute_cql_query = executeCQL(cql.toString());
            list = listbyQuery(execute_cql_query, persistenceClass);
        } catch (NumberFormatException | InstantiationException | IllegalAccessException ex) {
            LOOGER.error("Error during execute CQL", ex);

        }

        return list;
    }

    /**
     * The method for retrive the object from the result of Cassandra Query Language
     * @param resultCQL - The result of Cassandra Query Language
     * @param persistenceClass - The kind for retrieve  the Class
     * @return The result of List
     * @throws NumberFormatException
     * @throws InstantiationException
     * @throws IllegalAccessException 
     */
    public List listbyQuery(CqlResult resultCQL, Class persistenceClass) throws NumberFormatException, InstantiationException, IllegalAccessException {
        List<Map<String, ByteBuffer>> listMap = new ArrayList<>();

        for (CqlRow row : resultCQL.rows) {
            Map<String, ByteBuffer> mapColumn = new HashMap<>();

            for (Column cl : row.getColumns()) {

                mapColumn.put(EncodingUtil.byteToString(cl.name), cl.value);

            }
            listMap.add(mapColumn);
        }

        return getList(listMap, persistenceClass);

    }

    /**
     * The method for retrive a object from rowkey
     * @param key - The value of rowkey
     * @param persistenceClass - The kind class
     * The default  of consistency Level is ONE (ConsistencyLevel.ONE)
     * @return - The object from key
     * @see #findByKey(java.lang.Object, java.lang.Class, org.easycassandra.ConsistencyLevelCQL) 
     * @throws NotFoundException
     * @throws NumberFormatException
     * @throws InstantiationException
     * @throws IllegalAccessException 
     */
    public Object findByKey(Object key, Class persistenceClass) throws NotFoundException, NumberFormatException, InstantiationException, IllegalAccessException {
        return findByKey(key, persistenceClass, ConsistencyLevelCQL.ONE);
    }

    /**
     * The method for retrive a object from rowkey
     * @param key - The value of rowkey
     * @param persistenceClass - The kind class
     * @param consistencyLevel - The consistency Level
     * @return - The object from key
     * @throws NotFoundException
     * @throws NumberFormatException
     * @throws InstantiationException
     * @throws IllegalAccessException 
     */
    public Object findByKey(Object key, Class persistenceClass, ConsistencyLevelCQL consistencyLevel) throws NotFoundException, NumberFormatException, InstantiationException, IllegalAccessException {
        int limit = 1;
        List objects = new ArrayList<>();

        Field keyField = getKeyField(persistenceClass);
        ByteBuffer keyBuffer = writeMap.get(keyField.getType().getName()).getBytebyObject(key);
        String keyString = new UTF8Read().getObjectByByte(keyBuffer).toString();
        String condicao = "KEY";

        objects = retriveObject(condicao, keyString, objects, persistenceClass, consistencyLevel, limit);
        if (objects.size() > 0) {
            return objects.get(0);
        }
        return null;


    }

    //delete comand
    /**
     * Delete the Object from the key value
     * @param keyValue - The key value
     * @param objectClass  - The Kind of class
     * the default  of consistency Level is ONE (ConsistencyLevel.ONE)
     * The default lenght list is 10.000
     */
    public void deleteByKeyValue(Object keyValue, Class objectClass) {
        ByteBuffer keyBuffer = writeMap.get(getKeyField(objectClass).getType().getName()).getBytebyObject(keyValue);
        String keyString = new UTF8Read().getObjectByByte(keyBuffer).toString();


        runDeleteCqlCommand(keyString, objectClass);
    }

    /**
     * Delete the Object from the key value
     * @param keyObject - The Object for be delete
     */
    public void delete(Object keyObject) {
        Field keyField = getKeyField(keyObject.getClass());
        ByteBuffer keyBuffer = writeMap.get(keyField.getType().getName()).getBytebyObject(ReflectionUtil.getMethod(keyObject, keyField.getName()));
        String keyString = new UTF8Read().getObjectByByte(keyBuffer).toString();

        runDeleteCqlCommand(keyString, keyObject.getClass());


    }

    /**
     * Create the cql and remove the Object 
     * @param keyValue - The value for row key
     * @param persistenceClass - The kind object
     */
    protected void runDeleteCqlCommand(String keyValue, Class persistenceClass) {

        StringBuilder cql = new StringBuilder();
        cql.append("delete ");
        cql.append(columnNames(persistenceClass));
        cql.append(" from ");
        cql.append(getColumnFamilyName(persistenceClass));
        cql.append(" where KEY = '");
        cql.append(keyValue);
        cql.append("'");
        CqlResult cqlResult = executeCQL(cql.toString());

    }

    //find index
    /**
     * Find list objects from index
     * @param index - the index value
     * @param objectClass - Kind the Object
     * the default  of consistency Level is ONE (ConsistencyLevel.ONE)
     * The default lenght list is 10.000
     * @see #findByIndex(java.lang.Object, java.lang.Class, org.easycassandra.ConsistencyLevelCQL, int) 
     * @return list retrieve from the value index
     * 
     */
    public List findByIndex(Object index, Class objectClass) {
        return findByIndex(index, objectClass, ConsistencyLevelCQL.ONE);
    }

    /**
     * Find list objects from index
     * @param index - the index value
     * @param objectClass - Kind the Object
     * @param consistencyLevel - The consistency Level
     * @see #findByIndex(java.lang.Object, java.lang.Class, org.easycassandra.ConsistencyLevelCQL, int) 
     * The default lenght list is 10.000
     * @return list retrieve from the value index
     */
    public List findByIndex(Object index, Class objectClass, ConsistencyLevelCQL consistencyLevel) {
        return findByIndex(index, objectClass, consistencyLevel, 10000);
    }

    /**
     * Find list objects from index
     * @param index - the index value
     * @param objectClass - kind the Object
     * @param consistencyLevelCQL  - The consistency Level
     * @param limit - The length of List
     * @return  list retrieve from the value index
     */
    public List findByIndex(Object index, Class objectClass, ConsistencyLevelCQL consistencyLevelCQL, int limit) {
        List objects = new ArrayList<>();

        String indexString = index.toString();
        ColumnValue coluna = getIndexField(objectClass).getAnnotation(ColumnValue.class);
        String condicao = coluna.nome();


        return retriveObject(condicao, indexString, objects, objectClass, consistencyLevelCQL, limit);


    }

    /**
     * Update the Object
     * The default  of consistency Level is ONE (ConsistencyLevel.ONE)
     * @see #update(java.lang.Object, org.easycassandra.ConsistencyLevelCQL) 
     * @param object = The Object will updated 
     */
    public void update(Object object) {
        update(object, ConsistencyLevelCQL.ONE);
    }

    /**
     * Update the Object
     * @param object - The Object will updated
     * @param consistencyLevel - Th consistency Level
     */
    public void update(Object object, ConsistencyLevelCQL consistencyLevel) {
        StringBuilder cql = new StringBuilder();
        cql.append("UPDATE ");
        cql.append(getColumnFamilyName(object.getClass()));
        cql.append(" USING ").append(consistencyLevel.getValue()).append(" ");
        cql.append(" SET");
        List<String> strings = prepareCQLtoUpdate(object);
        int cont = 1;
        for (String string : strings) {
            cql.append(string);
            if (cont < strings.size()) {
                cql.append(" ,");
            }
            cont++;
        }

        cql.append(" where Key ='");
        cql.append("31");
        cql.append("'");
        executeCQL(cql.toString());
    }
}