package com.everteam.analytics.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.swagger.annotations.ApiModelProperty;




/**
 * InputDoc
 */
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2016-09-05T14:28:51.157Z")

public class InputDoc   {
  private String docId = null;

  private List<Field> fields = new ArrayList<Field>();

  public InputDoc docId(String docId) {
    this.docId = docId;
    return this;
  }

   /**
   * Get docId
   * @return docId
  **/
  @ApiModelProperty(value = "")
  public String getDocId() {
    return docId;
  }

  public void setDocId(String docId) {
    this.docId = docId;
  }

  public InputDoc fields(List<Field> fields) {
    this.fields = fields;
    return this;
  }

  public InputDoc addFieldsItem(Field fieldsItem) {
    this.fields.add(fieldsItem);
    return this;
  }

   /**
   * Get fields
   * @return fields
  **/
  @ApiModelProperty(value = "")
  public List<Field> getFields() {
    return fields;
  }

  public void setFields(List<Field> fields) {
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
    InputDoc inputDoc = (InputDoc) o;
    return Objects.equals(this.docId, inputDoc.docId) &&
        Objects.equals(this.fields, inputDoc.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(docId, fields);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class InputDoc {\n");
    
    sb.append("    docId: ").append(toIndentedString(docId)).append("\n");
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

