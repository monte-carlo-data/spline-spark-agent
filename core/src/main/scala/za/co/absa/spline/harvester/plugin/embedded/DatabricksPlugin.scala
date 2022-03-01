/*
 * Copyright 2020 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.spline.harvester.plugin.embedded

import javax.annotation.Priority
import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.catalog.CatalogTable
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, SubqueryAlias, View}
import org.apache.spark.sql.execution.datasources.LogicalRelation
import org.apache.spark.sql.{SaveMode, SparkSession}
import za.co.absa.commons.reflect.ReflectionUtils.extractFieldValue
import za.co.absa.commons.reflect.extractors.SafeTypeMatchingExtractor
import za.co.absa.spline.harvester.builder.SourceIdentifier
import za.co.absa.spline.harvester.plugin.Plugin.{Params, Precedence, ReadNodeInfo, WriteNodeInfo}
import za.co.absa.spline.harvester.plugin.embedded.DatabricksPlugin.`_: DataBricksCreateDeltaTableCommand`
import za.co.absa.spline.harvester.plugin.embedded.DatabricksPlugin.`_: DataBricksMergeIntoCommand`
import za.co.absa.spline.harvester.plugin.extractor.CatalogTableExtractor
import za.co.absa.spline.harvester.plugin.{Plugin, ReadNodeProcessing, WriteNodeProcessing}
import za.co.absa.spline.harvester.qualifier.PathQualifier

import scala.language.reflectiveCalls

@Priority(Precedence.Normal)
class DatabricksPlugin(
  pathQualifier: PathQualifier,
  session: SparkSession)
  extends Plugin
    with Logging
    with WriteNodeProcessing {

  private val extractor = new CatalogTableExtractor(session.catalog, pathQualifier)

  override val writeNodeProcessor: PartialFunction[LogicalPlan, WriteNodeInfo] = {
    case `_: DataBricksCreateDeltaTableCommand`(command) =>
      val table = extractFieldValue[CatalogTable](command, "table")
      val saveMode = extractFieldValue[SaveMode](command, "mode")
      val query = extractFieldValue[Option[LogicalPlan]](command, "query").get
      extractor.asTableWrite(table, saveMode, query)

    case `_: DataBricksMergeIntoCommand`(command) =>
      val target = extractFieldValue[LogicalPlan](command, "target")
      logInfo(s"Merge target: ${target}")
      target.children.foreach(child => {
        logInfo(s"Merge target child: ${child}")
        child.children.foreach(grandchild => {
          logInfo(s"Merge target grandchild (type=${grandchild.getClass.getName}): ${grandchild}")
          if (grandchild.isInstanceOf[LogicalRelation]) {
            var relation = grandchild.asInstanceOf[LogicalRelation]
            var table = relation.catalogTable
            if (table.isDefined) {
              logInfo(s"Merge target info: identifier=${table.get.identifier}, database=${table.get.identifier.database}, table=${table.get.identifier.table}, provider=${table.get.provider}, location=${table.get.storage.locationUri}")
            }
          }
        })
      })

      val source = extractFieldValue[LogicalPlan](command, "source")
      logInfo(s"Merge source: ${source}")
      source.children.foreach(child => {
        logInfo(s"Merge source child: ${child}")
        child.children.foreach(grandchild => {
          if (grandchild.isInstanceOf[View]) {
            val view = grandchild.asInstanceOf[View]
            logInfo(s"Merge source info: identifier=${view.desc.identifier.identifier}, database=${view.desc.identifier.database}, table=${view.desc.identifier.table}, provider=${view.desc.provider}, location=${view.desc.storage.locationUri}")
          }
        })
      })

      (SourceIdentifier(None, "foo"), SaveMode.Overwrite, target, Map("table" -> Map("identifier" -> "foo")))
  }
}

object DatabricksPlugin {

  private object `_: DataBricksCreateDeltaTableCommand` extends SafeTypeMatchingExtractor[AnyRef](
    "com.databricks.sql.transaction.tahoe.commands.CreateDeltaTableCommand")

  private object `_: DataBricksMergeIntoCommand` extends SafeTypeMatchingExtractor[AnyRef](
    "com.databricks.sql.transaction.tahoe.commands.MergeIntoCommandEdge"
  )
}
