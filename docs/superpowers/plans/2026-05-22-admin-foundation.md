# Admin Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first slice of phase 4: admin identity, user status, admin permission checks, and basic user management APIs.

**Architecture:** Reuse the existing Spring Boot layered style: Controller receives requests, Service handles business rules, Mapper persists through MyBatis-Plus. Admin permission is derived from the token user id and the user's `role`, not from any frontend parameter.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, MySQL, JWT, Vue 3 later for the admin UI.

---

## File Structure

- Modify `sql/xinyu.sql`: add `role` and `status` columns to `user`.
- Modify `xinyu-backend/src/main/java/com/xinyu/entity/User.java`: add `role` and `status` fields.
- Modify `xinyu-backend/src/main/java/com/xinyu/vo/UserVO.java`: return `role` and `status` to the frontend.
- Modify `xinyu-backend/src/main/java/com/xinyu/controller/UserController.java`: include role/status in login response and block disabled users.
- Modify `xinyu-backend/src/main/java/com/xinyu/service/UserService.java`: set defaults during registration and add admin/user management helpers.
- Create `xinyu-backend/src/main/java/com/xinyu/dto/UserStatusUpdateDTO.java`: request body for enabling/disabling users.
- Create `xinyu-backend/src/main/java/com/xinyu/controller/AdminUserController.java`: admin user list and status update APIs.

## Task 1: Add User Role And Status

**Knowledge point:** A login system answers "who are you"; an authorization system answers "what are you allowed to do." `role` supports authorization, and `status` lets admins disable accounts without deleting data.

- [x] **Step 1: Update SQL user table**

Add these columns to the `user` table:

```sql
`role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
```

- [x] **Step 2: Update User entity**

Add Java fields, getters, and setters:

```java
private String role;
private Integer status;
```

- [x] **Step 3: Set registration defaults**

In `UserService.register`, set:

```java
user.setRole("USER");
user.setStatus(1);
```

- [x] **Step 4: Compile backend**

Run:

```bash
mvn test
```

Expected: project compiles and existing context test passes.

## Task 2: Return Role And Block Disabled Login

**Knowledge point:** The frontend can hide admin pages, but backend permission must never depend on the frontend. Returning `role` only helps UI routing; the backend still checks role for every admin API.

- [x] **Step 1: Expand UserVO**

`UserVO` should contain:

```java
private Long id;
private String username;
private String nickname;
private String role;
private Integer status;
```

- [x] **Step 2: Block disabled users during login**

After password validation, if `status == 0`, return an error message instead of a token.

- [x] **Step 3: Return role/status in login and profile update**

Construct `UserVO` with all safe display fields.

- [x] **Step 4: Compile backend**

Run:

```bash
mvn test
```

Expected: project compiles and login response shape is updated.

## Task 3: Add Admin User APIs

**Knowledge point:** Admin APIs need two checks: authentication through token, then authorization through role. This is the core difference between "logged in" and "allowed."

- [x] **Step 1: Create UserStatusUpdateDTO**

```java
package com.xinyu.dto;

public class UserStatusUpdateDTO {
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
```

- [x] **Step 2: Add admin helpers in UserService**

Add methods to check admin role, query users by page, and update user status.

- [x] **Step 3: Create AdminUserController**

Provide:

```text
GET /admin/user/page
PUT /admin/user/{id}/status
```

- [x] **Step 4: Compile backend**

Run:

```bash
mvn test
```

Expected: project compiles and new endpoints are mapped.

## Task 4: Manual Verification

**Knowledge point:** For permission work, test both success and failure paths: no token, normal user token, admin token.

- [ ] **Step 1: Create or promote an admin user in the database**

```sql
UPDATE user SET role = 'ADMIN', status = 1 WHERE username = 'admin';
```

- [ ] **Step 2: Verify normal user cannot access admin API**

Call `GET /admin/user/page` with a normal user's token.

Expected: permission denied.

- [ ] **Step 3: Verify admin can list users**

Call `GET /admin/user/page?pageNum=1&pageSize=10` with an admin token.

Expected: paginated user list without passwords.

- [ ] **Step 4: Verify disabled user cannot login**

Call `PUT /admin/user/{id}/status` with `{"status":0}`, then try logging in as that user.

Expected: login is rejected.

## Self-Review

- Spec coverage: covers the first phase 4 foundation from `docs/11-分阶段开发计划.md`: admin role, admin permission recognition, user list, and user enable/disable.
- Intentional gaps: post audit, reply management, topic management, AI reply management, admin dashboard, and frontend admin pages are later slices.
- Placeholder scan: no placeholder implementation steps remain.
