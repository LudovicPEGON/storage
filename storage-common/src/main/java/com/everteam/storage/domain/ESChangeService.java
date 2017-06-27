package com.everteam.storage.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModelProperty;

/**
 * ESChangeService
 */
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2017-06-16T22:29:20.891Z")

public class ESChangeService   {
  @JsonProperty("type")
  private String type = null;

  @JsonProperty("maxrows")
  private Integer maxrows = null;

  @JsonProperty("scheduler")
  private Integer scheduler = null;

  @JsonProperty("params")
  private Map<String, String> params = new HashMap<String, String>();

  public ESChangeService type(String type) {
    this.type = type;
    return this;
  }

   /**
   * The change service type
   * @return type
  **/
  @ApiModelProperty(value = "The change service type")
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public ESChangeService maxrows(Integer maxrows) {
    this.maxrows = maxrows;
    return this;
  }

   /**
   * The maximum items treated in one time
   * @return maxrows
  **/
  @ApiModelProperty(value = "The maximum items treated in one time")
  public Integer getMaxrows() {
    return maxrows;
  }

  public void setMaxrows(Integer maxrows) {
    this.maxrows = maxrows;
  }

  public ESChangeService scheduler(Integer scheduler) {
    this.scheduler = scheduler;
    return this;
  }

   /**
   * The scheduler delay in seconds. 0 signifify no scheduler
   * @return scheduler
  **/
  @ApiModelProperty(value = "The scheduler delay in seconds. 0 signifify no scheduler")
  public Integer getScheduler() {
    return scheduler;
  }

  public void setScheduler(Integer scheduler) {
    this.scheduler = scheduler;
  }

  public ESChangeService params(Map<String, String> params) {
    this.params = params;
    return this;
  }

  public ESChangeService putParamsItem(String key, String paramsItem) {
    this.params.put(key, paramsItem);
    return this;
  }

   /**
   * The change service parameters
   * @return params
  **/
  @ApiModelProperty(value = "The change service parameters")
  public Map<String, String> getParams() {
    return params;
  }

  public void setParams(Map<String, String> params) {
    this.params = params;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ESChangeService esChangeService = (ESChangeService) o;
    return Objects.equals(this.type, esChangeService.type) &&
        Objects.equals(this.maxrows, esChangeService.maxrows) &&
        Objects.equals(this.scheduler, esChangeService.scheduler) &&
        Objects.equals(this.params, esChangeService.params);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, maxrows, scheduler, params);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ESChangeService {\n");
    
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    maxrows: ").append(toIndentedString(maxrows)).append("\n");
    sb.append("    scheduler: ").append(toIndentedString(scheduler)).append("\n");
    sb.append("    params: ").append(toIndentedString(params)).append("\n");
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

