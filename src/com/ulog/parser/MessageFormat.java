package com.ulog.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class MessageFormat {

    private String name;
    private List<Object[]> fields; 

    public MessageFormat(byte[] data) {
        String[] formatArr = ULogUtils.parseString(data).split(":");
        this.name = formatArr[0];
        String[] typesStr = formatArr[1].split(";");
        this.fields = new ArrayList<>();

        for (String t : typesStr) {
            if (!t.isEmpty()) {
                this.fields.add(extractType(t));
            }
        }
    }

    public String getName() {
        return this.name;
    }

    public List<Object[]> getFields() {
        return this.fields;
    }

    private Object[] extractType(String typeStr) {
        String type_name = "";
        int arraySize = 0;

        String[] field_str_split = typeStr.split(" ");
        String type_str = field_str_split[0];
        String name_str = field_str_split[1];
        int aPos = typeStr.indexOf('[');
        if (aPos == -1) {
            arraySize = 1;
            type_name = type_str;
        } else {
            int bPos = typeStr.indexOf(']');
            arraySize = Integer.parseInt(typeStr.substring(aPos + 1, bPos));
            type_name = typeStr.substring(0, aPos);

        }
        return new Object[]{type_name, arraySize, name_str};
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        MessageFormat other = (MessageFormat) obj;
        return Objects.equals(this.name, other.name)
                && Objects.equals(this.fields, other.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fields);
    }
}
