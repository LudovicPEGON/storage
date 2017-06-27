package com.everteam.analytics.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModelProperty;

/**
 * Updates
 */
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2017-06-18T14:12:49.113Z")

public class Updates   {
  @JsonProperty("query")
  private String query = null;

  @JsonProperty("rows")
  private Integer rows = null;

  @JsonProperty("fields")
  private Map<String, Object> fields = new HashMap<String, Object>();

  public Updates query(String query) {
    this.query = query;
    return this;
  }

   /**
   * Query used to retrieve all document to update
   * @return query
  **/
  @ApiModelProperty(value = "Query used to retrieve all document to update")
  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public Updates rows(Integer rows) {
    this.rows = rows;
    return this;
  }

   /**
   * Query used to retrieve all document to update
   * @return rows
  **/
  @ApiModelProperty(value = "Query used to retrieve all document to update")
  public Integer getRows() {
    return rows;
  }

  public void setRows(Integer rows) {
    this.rows = rows;
  }

  public Updates fields(Map<String, Object> fields) {
    this.fields = fields;
    return this;
  }

  public Updates putFieldsItem(String key, Object fieldsItem) {
    this.fields.put(key, fieldsItem);
    return this;
  }

   /**
   * Field list to update.  Associated values can be a simple string or an object. This object can have several keys like  add, remove, set, inc... values can be a string or a array of string.
   * @return fields
  **/
  @ApiModelProperty(value = "Field list to update.  Associated values can be a simple string or an object. This object can have several keys like  add, remove, set, inc... values can be a string or a array of string.")
  public Map<String, Object> getFields() {
    return fields;
  }

  public void setFields(Map<String, Object> fields) {
    this.fields = fields;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Updates updates = (Updates) o;
    return Objects.equals(this.query, updates.query) &&
        Objects.equals(this.rows, updates.rows) &&
        Objects.equals(this.fields, updates.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(query, rows, fields);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Updates {\n");
    
    sb.append("    query: ").append(toIndentedString(query)).append("\n");
    sb.append("    rows: ").append(toIndentedString(rows)).append("\n");
    sb.append("    fields: ").append(toIndentedString(fields)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

