/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.newcodeperiod.ws;

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.util.UuidFactoryFast;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.newcodeperiod.NewCodePeriodDao;
import org.sonar.db.newcodeperiod.NewCodePeriodDbTester;
import org.sonar.db.newcodeperiod.NewCodePeriodDto;
import org.sonar.db.newcodeperiod.NewCodePeriodType;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.TestComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.NewCodePeriods;
import org.sonarqube.ws.NewCodePeriods.ListWSResponse;
import org.sonarqube.ws.NewCodePeriods.ShowWSResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class ListActionTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private ComponentDbTester componentDb = new ComponentDbTester(db);
  private DbClient dbClient = db.getDbClient();
  private ComponentFinder componentFinder = TestComponentFinder.from(db);
  private NewCodePeriodDao dao = new NewCodePeriodDao(System2.INSTANCE, UuidFactoryFast.getInstance());
  private NewCodePeriodDbTester tester = new NewCodePeriodDbTester(db);
  private ListAction underTest = new ListAction(dbClient, userSession, componentFinder, dao);
  private WsActionTester ws = new WsActionTester(underTest);

  @Test
  public void test_definition() {
    WebService.Action definition = ws.getDef();

    assertThat(definition.key()).isEqualTo("list");
    assertThat(definition.isInternal()).isFalse();
    assertThat(definition.since()).isEqualTo("8.0");
    assertThat(definition.isPost()).isFalse();

    assertThat(definition.params()).extracting(WebService.Param::key).containsOnly("project");
    assertThat(definition.param("project").isRequired()).isTrue();
  }

  @Test
  public void throw_NFE_if_project_not_found() {
    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("Component key 'unknown' not found");

    ws.newRequest()
      .setParam("project", "unknown")
      .execute();
  }

  @Test
  public void throw_FE_if_no_project_permission() {
    ComponentDto project = componentDb.insertMainBranch();
    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");

    ws.newRequest()
      .setParam("project", project.getKey())
      .execute();
  }

  @Test
  public void list_only_LLB() {
    ComponentDto project = componentDb.insertMainBranch();

    createBranches(project, 5, BranchType.LONG);
    createBranches(project, 3, BranchType.SHORT);

    logInAsProjectAdministrator(project);

    ListWSResponse response = ws.newRequest()
      .setParam("project", project.getKey())
      .executeProtobuf(ListWSResponse.class);

    assertThat(response).isNotNull();
    assertThat(response.getNewCodePeriodsCount()).isEqualTo(6);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getBranchKey)
      .contains("master", "LONG_0", "LONG_1", "LONG_2", "LONG_3", "LONG_4");

    //check if global default is set
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getType)
      .contains(NewCodePeriods.NewCodePeriodType.PREVIOUS_VERSION);
  }

  @Test
  public void list_inherited_global_settings() {
    ComponentDto project = componentDb.insertMainBranch();
    tester.insert(new NewCodePeriodDto().setType(NewCodePeriodType.SPECIFIC_ANALYSIS).setValue("uuid"));

    createBranches(project, 5, BranchType.LONG);

    logInAsProjectAdministrator(project);

    ListWSResponse response = ws.newRequest()
      .setParam("project", project.getKey())
      .executeProtobuf(ListWSResponse.class);

    assertThat(response).isNotNull();
    assertThat(response.getNewCodePeriodsCount()).isEqualTo(6);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getBranchKey)
      .contains("master", "LONG_0", "LONG_1", "LONG_2", "LONG_3", "LONG_4");

    //check if global default is set
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getType)
      .contains(NewCodePeriods.NewCodePeriodType.SPECIFIC_ANALYSIS);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getValue)
      .contains("uuid");
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getInherited)
      .contains(true);
  }

  @Test
  public void list_inherited_project_settings() {
    ComponentDto projectWithOwnSettings = componentDb.insertMainBranch();
    ComponentDto projectWithGlobalSettings = componentDb.insertMainBranch();
    tester.insert(new NewCodePeriodDto()
      .setType(NewCodePeriodType.SPECIFIC_ANALYSIS)
      .setValue("global_uuid"));
    tester.insert(new NewCodePeriodDto()
      .setProjectUuid(projectWithOwnSettings.uuid())
      .setType(NewCodePeriodType.SPECIFIC_ANALYSIS)
      .setValue("project_uuid"));

    createBranches(projectWithOwnSettings, 5, BranchType.LONG);

    logInAsProjectAdministrator(projectWithOwnSettings, projectWithGlobalSettings);

    ListWSResponse response = ws.newRequest()
      .setParam("project", projectWithOwnSettings.getKey())
      .executeProtobuf(ListWSResponse.class);

    //verify project with project level settings
    assertThat(response).isNotNull();
    assertThat(response.getNewCodePeriodsCount()).isEqualTo(6);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getBranchKey)
      .contains("master", "LONG_0", "LONG_1", "LONG_2", "LONG_3", "LONG_4");

    //check if project setting is set
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getType)
      .contains(NewCodePeriods.NewCodePeriodType.SPECIFIC_ANALYSIS);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getValue)
      .containsOnly("project_uuid");
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getInherited)
      .containsOnly(true);

    //verify project with global level settings
    response = ws.newRequest()
      .setParam("project", projectWithGlobalSettings.getKey())
      .executeProtobuf(ListWSResponse.class);

    assertThat(response).isNotNull();
    assertThat(response.getNewCodePeriodsCount()).isEqualTo(1);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getBranchKey)
      .containsOnly("master");

    //check if global setting is set
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getType)
      .contains(NewCodePeriods.NewCodePeriodType.SPECIFIC_ANALYSIS);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getValue)
      .contains("global_uuid");
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getInherited)
      .containsOnly(true);
  }

  @Test
  public void list_branch_and_inherited_global_settings() {
    ComponentDto project = componentDb.insertMainBranch();
    ComponentDto branchWithOwnSettings = componentDb.insertProjectBranch(project, branchDto -> branchDto.setKey("OWN_SETTINGS"));
    componentDb.insertProjectBranch(project, branchDto -> branchDto.setKey("GLOBAL_SETTINGS"));

    tester.insert(new NewCodePeriodDto()
      .setType(NewCodePeriodType.SPECIFIC_ANALYSIS)
      .setValue("global_uuid"));

    tester.insert(new NewCodePeriodDto()
      .setProjectUuid(project.uuid())
      .setBranchUuid(branchWithOwnSettings.uuid())
      .setType(NewCodePeriodType.SPECIFIC_ANALYSIS)
      .setValue("branch_uuid"));

    logInAsProjectAdministrator(project);

    ListWSResponse response = ws.newRequest()
      .setParam("project", project.getKey())
      .executeProtobuf(ListWSResponse.class);

    assertThat(response).isNotNull();
    assertThat(response.getNewCodePeriodsCount()).isEqualTo(3);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getBranchKey)
      .contains("master", "OWN_SETTINGS", "GLOBAL_SETTINGS");

    Optional<ShowWSResponse> ownSettings = response.getNewCodePeriodsList().stream()
      .filter(s -> !s.getInherited())
      .findFirst();

    assertThat(ownSettings).isNotNull();
    assertThat(ownSettings).isNotEmpty();
    assertThat(ownSettings.get().getProjectKey()).isEqualTo(project.getKey());
    assertThat(ownSettings.get().getBranchKey()).isEqualTo("OWN_SETTINGS");
    assertThat(ownSettings.get().getType()).isEqualTo(NewCodePeriods.NewCodePeriodType.SPECIFIC_ANALYSIS);
    assertThat(ownSettings.get().getValue()).isEqualTo("branch_uuid");
    assertThat(ownSettings.get().getInherited()).isFalse();

    //check if global default is set
    assertThat(response.getNewCodePeriodsList())
      .filteredOn(ShowWSResponse::getInherited)
      .extracting(ShowWSResponse::getType)
      .contains(NewCodePeriods.NewCodePeriodType.SPECIFIC_ANALYSIS);
    assertThat(response.getNewCodePeriodsList())
      .filteredOn(ShowWSResponse::getInherited)
      .extracting(ShowWSResponse::getValue)
      .contains("global_uuid");
  }

  @Test
  public void list_branch_and_inherited_project_settings() {
    ComponentDto project = componentDb.insertMainBranch();
    ComponentDto branchWithOwnSettings = componentDb.insertProjectBranch(project, branchDto -> branchDto.setKey("OWN_SETTINGS"));
    componentDb.insertProjectBranch(project, branchDto -> branchDto.setKey("PROJECT_SETTINGS"));

    tester.insert(new NewCodePeriodDto()
      .setType(NewCodePeriodType.SPECIFIC_ANALYSIS)
      .setValue("global_uuid"));

    tester.insert(new NewCodePeriodDto()
      .setProjectUuid(project.uuid())
      .setType(NewCodePeriodType.SPECIFIC_ANALYSIS)
      .setValue("project_uuid"));

    tester.insert(new NewCodePeriodDto()
      .setProjectUuid(project.uuid())
      .setBranchUuid(branchWithOwnSettings.uuid())
      .setType(NewCodePeriodType.SPECIFIC_ANALYSIS)
      .setValue("branch_uuid"));

    logInAsProjectAdministrator(project);

    ListWSResponse response = ws.newRequest()
      .setParam("project", project.getKey())
      .executeProtobuf(ListWSResponse.class);

    assertThat(response).isNotNull();
    assertThat(response.getNewCodePeriodsCount()).isEqualTo(3);
    assertThat(response.getNewCodePeriodsList()).extracting(ShowWSResponse::getBranchKey)
      .contains("master", "OWN_SETTINGS", "PROJECT_SETTINGS");

    Optional<ShowWSResponse> ownSettings = response.getNewCodePeriodsList().stream()
      .filter(s -> !s.getInherited())
      .findFirst();

    assertThat(ownSettings).isNotNull();
    assertThat(ownSettings).isNotEmpty();
    assertThat(ownSettings.get().getProjectKey()).isEqualTo(project.getKey());
    assertThat(ownSettings.get().getBranchKey()).isEqualTo("OWN_SETTINGS");
    assertThat(ownSettings.get().getType()).isEqualTo(NewCodePeriods.NewCodePeriodType.SPECIFIC_ANALYSIS);
    assertThat(ownSettings.get().getValue()).isEqualTo("branch_uuid");
    assertThat(ownSettings.get().getInherited()).isFalse();

    //check if global default is set
    assertThat(response.getNewCodePeriodsList())
      .filteredOn(ShowWSResponse::getInherited)
      .extracting(ShowWSResponse::getType)
      .contains(NewCodePeriods.NewCodePeriodType.SPECIFIC_ANALYSIS);
    assertThat(response.getNewCodePeriodsList())
      .filteredOn(ShowWSResponse::getInherited)
      .extracting(ShowWSResponse::getValue)
      .contains("project_uuid");
  }

  private void createBranches(ComponentDto project, int numberOfBranches, BranchType branchType) {
    for (int branchCount = 0; branchCount < numberOfBranches; branchCount++) {
      String branchKey = String.format("%s_%d", branchType.name(), branchCount);
      componentDb.insertProjectBranch(project, branchDto -> branchDto.setKey(branchKey).setBranchType(branchType));
    }
  }

  private void logInAsProjectAdministrator(ComponentDto... project) {
    userSession.logIn().addProjectPermission(UserRole.ADMIN, project);
  }
}