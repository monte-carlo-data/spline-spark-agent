/*
 * Copyright 2019 ABSA Group Limited
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

package za.co.absa.spline.harvester.builder

import org.apache.spark.sql.catalyst.expressions.{Alias, Attribute, Expression}
import org.apache.spark.sql.catalyst.plans.logical.Project
import za.co.absa.spline.harvester.IdGenerators
import za.co.absa.spline.harvester.converter.{DataConverter, DataTypeConverter}
import za.co.absa.spline.harvester.postprocessing.PostProcessor

class ProjectNodeBuilder
  (operation: Project)
  (idGenerators: IdGenerators, dataTypeConverter: DataTypeConverter, dataConverter: DataConverter, postProcessor: PostProcessor)
  extends GenericNodeBuilder(operation)(idGenerators, dataTypeConverter, dataConverter, postProcessor) {

  override def resolveAttributeChild(attribute: Attribute): Option[Expression] = {
    operation.projectList
      .find(_.exprId == attribute.exprId)
      .map(_.asInstanceOf[Alias])
  }
}
