/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.cypher.internal.parser

import org.parboiled.scala.Rule1

class BaseRulesTest extends ParserTest[Any, Any] with Base {

  test("testWhitespaceHandling") {
    implicit val parserToTest: Rule1[Boolean] = "a" ~ WS ~ "b" ~ push(true)

    parsing("a b") shouldGive true
    parsing("a　b") shouldGive true
  }

  def convert(result: Any): Any = result
}
