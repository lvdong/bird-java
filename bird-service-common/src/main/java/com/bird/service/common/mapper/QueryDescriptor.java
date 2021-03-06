package com.bird.service.common.mapper;

import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableName;
import com.bird.service.common.service.query.FilterGroup;
import com.bird.service.common.service.query.FilterOperate;
import com.bird.service.common.service.query.FilterRule;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author liuxx
 * @date 2018/9/28
 */
public class QueryDescriptor implements Serializable {

    private static final Map<String, String> OPERATE_MAP = new HashMap<>();

    static {
        OPERATE_MAP.put(FilterOperate.AND, "and");
        OPERATE_MAP.put(FilterOperate.OR, "or");
        OPERATE_MAP.put(FilterOperate.EQUAL, "=");
        OPERATE_MAP.put(FilterOperate.NOTEQUAL, "!=");
        OPERATE_MAP.put(FilterOperate.LESS, "<");
        OPERATE_MAP.put(FilterOperate.LESSOREQUAL, "<=");
        OPERATE_MAP.put(FilterOperate.GREATER, ">");
        OPERATE_MAP.put(FilterOperate.GREATEROREQUAL, ">=");
        OPERATE_MAP.put(FilterOperate.STARTWITH, "like");
        OPERATE_MAP.put(FilterOperate.ENDWITH, "like");
        OPERATE_MAP.put(FilterOperate.CONTAINS, "like");
        OPERATE_MAP.put(FilterOperate.IN, "in");

    }

    private String select;
    private String from;
    private Map<String, String> fieldMap;

    private QueryDescriptor(String select, String from, Map<String, String> fieldMap) {
        this.select = select;
        this.from = from;
        this.fieldMap = fieldMap;
    }

    public static QueryDescriptor parseClass(Class<?> tClass) {
        Map<String, String> fieldMap = new HashMap<>(16);

        if (tClass == null) return null;
        Class tempClass = tClass;
        StringBuilder sb = new StringBuilder();

        while (tempClass != null && !tempClass.getName().equals(Object.class.getName())) {
            Field[] tempFields = tempClass.getDeclaredFields();
            for (Field field : tempFields) {
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && !tableField.exist()) continue;

                String fieldName = StringUtils.wrapIfMissing(field.getName(), '`');
                String dbFieldName = getDbFieldName(field);
                fieldMap.putIfAbsent(fieldName, dbFieldName);
            }

            tempClass = tempClass.getSuperclass();
        }

        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            sb.append(entry.getValue());
            sb.append(" AS ");
            sb.append(entry.getKey());
            sb.append(",");
        }

        String select = StringUtils.stripEnd(sb.toString(), ",");

        String from = "";
        TableName tableName = tClass.getAnnotation(TableName.class);
        if (tableName != null) {
            from = tableName.value();
        }
        return new QueryDescriptor(select, from, fieldMap);
    }

    public static String getDbFieldName(Field field) {
        TableField tableField = field.getAnnotation(TableField.class);
        String dbFieldName = tableField == null ? field.getName() : tableField.value();

        if (StringUtils.startsWith(dbFieldName, "{") && StringUtils.endsWith(dbFieldName, "}")) {
            dbFieldName = StringUtils.removeStart(dbFieldName, "{");
            dbFieldName = StringUtils.removeEnd(dbFieldName, "}");
        } else {
            dbFieldName = StringUtils.wrapIfMissing(dbFieldName, '`');
        }
        return dbFieldName;
    }

    String getSelect() {
        return select;
    }

    String getFrom() {
        return from;
    }

    String getDbFieldName(String field) {
        String emptyField = "''";
        String dbDelimiter = ".";
        if (StringUtils.isBlank(field)) return emptyField;
        if (field.contains(dbDelimiter)) return field;

        String fieldName = StringUtils.wrapIfMissing(field, '`');
        return this.fieldMap.getOrDefault(fieldName, fieldName);
    }

    public String formatFilters(FilterGroup group) {
        if (group == null) return null;

        String where = formatRules(group.getRules());

        boolean isStart = true;
        StringBuilder groupBuilder = new StringBuilder();
        if (CollectionUtils.isNotEmpty(group.getGroups())) {
            for (FilterGroup innerGroup : group.getGroups()) {
                String innerWhere = this.formatFilters(innerGroup);
                if (StringUtils.isNotBlank(innerWhere)) {
                    if (!isStart) {
                        groupBuilder.append(" and ");
                    }
                    groupBuilder.append(innerWhere);
                    isStart = false;
                }
            }
        }
        String groupWhere = groupBuilder.toString();

        if (StringUtils.isBlank(groupWhere)) return where;
        else if (StringUtils.isBlank(where)) return groupWhere;
        else {
            StringBuilder sb = new StringBuilder();
            String operate = StringUtils.isBlank(group.getOperate())
                    ? FilterOperate.AND :
                    OPERATE_MAP.getOrDefault(group.getOperate().toLowerCase(), FilterOperate.AND);

            sb.append("(").append(where).append(" ").append(operate).append(" ").append(groupWhere).append(")");
            return sb.toString();
        }
    }

    private String formatRules(List<FilterRule> rules) {
        StringBuilder sb = new StringBuilder();

        boolean isStart = true;
        for (FilterRule rule : rules) {
            String field = StringUtils.strip(rule.getField());
            String value = StringUtils.strip(rule.getValue());
            if (StringUtils.isBlank(field) || StringUtils.isBlank(value)) continue;
//            if (!validateSqlValue(value)) continue;

            if (!OPERATE_MAP.containsKey(rule.getOperate())) {
                rule.setOperate(FilterOperate.EQUAL);
            }
            if (!isStart) {
                sb.append(" and ");
            }

            if (StringUtils.equals(rule.getOperate(), FilterOperate.IN)) {
                sb.append(String.format("FIND_IN_SET(%s,%s)", this.getDbFieldName(field), StringUtils.wrapIfMissing(StringUtils.strip(value, ","), "'")));
            } else {
                switch (rule.getOperate()) {
                    case FilterOperate.STARTWITH:
                        value = value + "%";
                        break;
                    case FilterOperate.ENDWITH:
                        value = "%" + value;
                        break;
                    case FilterOperate.CONTAINS:
                        value = "%" + value + "%";
                        break;
                    default:
                        break;
                }
                value = StringUtils.wrapIfMissing(value, "'");
                sb.append(this.getDbFieldName(field)).append(" ").append(OPERATE_MAP.get(rule.getOperate().toLowerCase())).append(" ").append(value);
            }
            isStart = false;
        }
        String where = sb.toString();
        return StringUtils.isNotBlank(where) ? "(" + where + ")" : where;
    }
}
