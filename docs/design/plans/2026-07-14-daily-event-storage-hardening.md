# 起居注每日事件存储改进开发计划

日期：2026-07-14

状态：已实施（事件存储设备测试通过；另有 2 条既有 Compose UI 用例需单独处理）

适用版本：Room 数据库 `3 -> 4`

## 1. 目标

在不扩展产品范围的前提下，把每日事件记录从“当前可用”提升为“适合长期保存”：

- 事件一旦保存，其所属自然日和本地时间不会因为设备时区变化而漂移。
- 手动新增、编辑和 AI 导入遵守同一套数据约束。
- 导入中的异常时间不再被静默改写为导入当下。
- 重复导入同一份原文不会产生重复事件。
- 时间线和日期范围查询具有稳定顺序，并有匹配的索引。
- 版本 1、2、3 的既有事件都能无损升级到版本 4。
- 七日省察仍只读取事件，不改变已确认的生成、快照和聊天行为。

本计划只处理每日事件的持久化和直接消费链路。若与早期时间段文档冲突，以本计划为准，具体覆盖以下旧约定：

1. 历史事件不再始终按设备当前时区重新计算所属日期。
2. 导入时间无法解析或区间倒置时，不再把起止时间都替换为导入当下。

## 2. 当前基线

当前事件表为 `note_records`，包含：

- `id`
- `eventText`
- `moodTag`
- `moodNote`
- `startedAt`
- `endedAt`
- `createdAt`
- `updatedAt`

已有能力：

- 手动新增、编辑、删除。
- Room `Flow` 驱动时间线更新。
- 零分钟和跨日时间段。
- AI 批量导入。
- 编辑保留 `createdAt`，更新 `updatedAt`。
- Repository 拒绝 `endedAt < startedAt`。
- `1 -> 2 -> 3` 保留式迁移和版本 3 schema 导出。
- 数据库纳入 Android 云备份和设备迁移。

当前主要缺口：

- 只有绝对时间戳，没有稳定的事件日期和录入时区。
- 表没有与查询匹配的索引，排序没有最终 `id` 次序。
- 手动表单、导入和 Repository 的校验不一致。
- 导入异常时间会被改成导入时间，可能把旧事件放到今天。
- 导入没有稳定来源标识，同一原文可以重复写入。
- 真实 Room 查询边界、索引和唯一约束缺少数据库级测试。

## 3. 范围

### 3.1 本次实现

- 扩展 `note_records`，保存稳定日期、原始时区和记录来源。
- 数据库升级到版本 4，并添加 `3 -> 4` 保留式迁移。
- Repository 统一执行正文、心情、时间、日期和来源校验。
- 时间线和七日省察按稳定事件日期读取。
- 导入采用显式时间纠错和整批原子保存。
- 为导入建立稳定批次键，阻止同一原文重复导入。
- 添加时间、日期和导入索引。
- 补齐 Repository、ViewModel、DAO 和迁移测试。

### 3.2 明确不做

- 不建立“每天一张表”或单独的日期主表。
- 不增加搜索、心情筛选、时长统计、云同步、导出或批量清除。
- 不增加实时计时器、多时间段或重复事件规则。
- 不自动拆分跨日事件；一条事件仍只保存一行。
- 不为事件增加修改历史、软删除或回收站。
- 不改变七日省察的七日窗口、不可重生成、快照或聊天规则。
- 不添加新的前端视觉结构；现有错误提示区域足以承载导入校验结果。

## 4. 核心数据语义

### 4.1 三类时间不可混用

| 字段 | 语义 | 是否可编辑 |
|---|---|---|
| `startedAt` | 事件开始的绝对时刻，Unix 毫秒 | 是 |
| `endedAt` | 事件结束的绝对时刻，Unix 毫秒 | 是 |
| `eventDate` | 事件在原始时区中的开始日期，ISO `yyyy-MM-dd` | 由 Repository 计算 |
| `zoneId` | 创建事件时间语义时使用的 IANA 时区 | 随时间语义保存 |
| `createdAt` | 本地记录行首次建立的时刻 | 否 |
| `updatedAt` | 本地记录行最后修改的时刻 | 保存时更新 |

`eventDate` 是“归入哪一天”的稳定事实，不是展示时临时推导的缓存。`startedAt`、`endedAt` 仍是跨设备和夏令时安全的绝对时刻。

### 4.2 日期归属

- 普通事件：`eventDate = startedAt` 在 `zoneId` 中对应的本地日期。
- 跨日事件：只归入开始日期，不在结束日期重复出现。
- 零分钟事件：允许 `startedAt == endedAt`。
- 设备时区变化：不修改既有 `eventDate` 或 `zoneId`。
- 新建事件：使用创建表单时的 `Clock.zone`。
- 编辑正文、心情：保留原 `startedAt`、`endedAt`、`eventDate` 和 `zoneId`。
- 编辑起止时间：继续在该记录已有 `zoneId` 中解释选择结果，并重新计算 `eventDate`。

时间线卡片使用记录自身的 `zoneId` 格式化起止时间。这样用户旅行后仍看到当初记录的本地钟表时间。

### 4.3 “今日”和“昨日”标签

`eventDate` 决定分组；标签仍以当前页面的 `Clock` 决定：

- `eventDate == LocalDate.now(clock)`：`今日`
- `eventDate == LocalDate.now(clock).minusDays(1)`：`昨日`
- 其他日期：显示 ISO 日期

时区变化不会改变事件分组日期，只可能改变哪个固定日期此刻被称作“今日”或“昨日”。

## 5. Room 版本 4 表结构

### 5.1 实体建议

```kotlin
@Entity(
    tableName = "note_records",
    indices = [
        Index(
            name = "index_note_records_eventDate_startedAt_createdAt_id",
            value = ["eventDate", "startedAt", "createdAt", "id"],
        ),
        Index(
            name = "index_note_records_startedAt_createdAt_id",
            value = ["startedAt", "createdAt", "id"],
        ),
        Index(
            name = "index_note_records_importBatchId_importOrdinal",
            value = ["importBatchId", "importOrdinal"],
            unique = true,
        ),
    ],
)
data class NoteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventText: String,
    val moodTag: String?,
    val moodNote: String?,
    val startedAt: Long,
    val endedAt: Long,
    val eventDate: String,
    val zoneId: String,
    val source: String,
    val importBatchId: String?,
    val importOrdinal: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

实现中为新字段保留了构造器默认值，以兼容旧的测试夹具和本地调用；所有正式写入仍由 Repository 重新计算并校验这些字段。

`id` 继续使用 Room 自增主键。心情仍使用可空 `TEXT`，不拆分心情表，因为当前是一条记录最多一个固定枚举标签，不存在独立实体关系。

### 5.2 来源字段

`source` 只允许：

- `manual`
- `import`

来源组合必须满足：

| `source` | `importBatchId` | `importOrdinal` |
|---|---|---|
| `manual` | `null` | `null` |
| `import` | 非空 | `>= 0` |

`importBatchId` 是规范化原始导入文本的 SHA-256 十六进制摘要；`importOrdinal` 是该解析批次内从 0 开始的事件序号。唯一索引保证同一原文的同一序号只能写入一次。SQLite 允许唯一索引中存在多个 `NULL`，因此手动事件不会互相冲突。

不把事件正文和时间设为全局唯一，因为用户可能在不同场景真实记录相同内容。

### 5.3 领域约束

所有新增、更新和导入必须经过 Repository，并满足：

```text
trim(eventText) 非空
endedAt >= startedAt
moodTag 为空或属于 MoodLabels.ALL
eventDate 可被 LocalDate.parse
zoneId 可被 ZoneId.of 解析
eventDate == startedAt 在 zoneId 中的本地日期
source/importBatchId/importOrdinal 组合合法
```

正文和心情补述保存前统一 `trim`；空白心情补述保存为 `null`。

本次不额外引入 SQLite trigger。原因是 Room 实体无法直接声明完整 `CHECK`，同时在新建回调和每次迁移维护 trigger 会形成第二套规则源。Repository 是唯一业务写入口，DAO 数据库测试负责证明各入口没有绕过它。若未来出现多个独立写入进程，再单独评估数据库 trigger。

## 6. DAO 查询与返回契约

### 6.1 完整时间线

```sql
SELECT * FROM note_records
ORDER BY startedAt DESC, createdAt DESC, id DESC
```

必须添加 `id` 作为最终排序键。AI 导入的一批记录共享 `createdAt`，且可能共享 `startedAt`；没有 `id` 时，同时间事件顺序不稳定。

当前版本继续使用 `Flow<List<NoteRecord>>` 返回全部记录。个人日记在可预见规模内可以接受；分页不属于本次范围。复合索引必须先补齐，避免排序临时表。

### 6.2 稳定日期范围

七日省察及未来按日读取改为日期字符串查询：

```sql
SELECT * FROM note_records
WHERE eventDate >= :startDateInclusive
  AND eventDate < :endDateExclusive
ORDER BY eventDate ASC, startedAt ASC, createdAt ASC, id ASC
```

参数使用 ISO 日期字符串。ISO `yyyy-MM-dd` 的字典顺序与日期顺序一致。

DAO 接口建议为：

```kotlin
suspend fun recordsInDateRange(
    startDateInclusive: String,
    endDateExclusive: String,
): List<NoteRecord>
```

删除或停止使用基于当前时区换算毫秒边界的 `recordsInRange(startMillis, endExclusiveMillis)`。这样七日省察读取的是事件固定归属日，而不是查询当下重新解释后的日期。

### 6.3 更新和删除结果

DAO 的更新、删除方法返回受影响行数：

```kotlin
@Update
suspend fun update(record: NoteRecord): Int

@Delete
suspend fun delete(record: NoteRecord): Int
```

Repository 在返回值不是 1 时抛出明确异常，避免记录已经不存在时界面仍显示保存或删除成功。

## 7. Repository 作为唯一写入边界

### 7.1 新增

Repository 不接收调用方计算的 `eventDate`，而是接收事件内容、起止时间、`zoneId` 和 `nowMillis`，完成：

1. 清理文本。
2. 校验心情和时间区间。
3. 从 `startedAt + zoneId` 计算 `eventDate`。
4. 写入 `source = manual`。
5. 写入 `createdAt = updatedAt = nowMillis`。

### 7.2 编辑

编辑必须保证：

- 保留 `id`、`createdAt` 和来源字段。
- 使用记录已有 `zoneId` 解释编辑后的时间。
- 重新计算 `eventDate`。
- 只把 `updatedAt` 改为当前保存时间。
- DAO 更新行数必须为 1。

为减少 ViewModel 构造不一致实体的机会，优先把 `RecordOperations.updateRecord(record)` 收窄为显式参数或 `RecordEdit` 值对象，由 Repository 生成最终实体。若本次控制改动规模而保留现有签名，Repository 仍必须覆盖调用方传入的 `eventDate`、来源和元数据字段，不能直接信任整个实体。

### 7.3 删除

- 保留现有确认对话框。
- 删除只按主键执行。
- DAO 返回 0 时显示失败，不应静默视为成功。
- 不增加软删除或撤销。

## 8. AI 导入改进

### 8.1 规范化顺序

导入流程固定为：

1. 读取并规范化用户原文：统一换行、去除首尾空白，不改正文内部内容。
2. 计算稳定 `importBatchId`。
3. 调用解析服务。
4. 将每条解析结果转换为候选事件。
5. 对整批候选事件执行校验。
6. 整批通过后一次事务写入。
7. 任何一条无法安全解释时，整批不写入。

### 8.2 时间纠错矩阵

| 开始时间 | 结束时间 | 处理 |
|---|---|---|
| 有效 | 有效且 `end >= start` | 保存完整区间 |
| 有效 | 缺失或无效 | 保存为开始时间的零分钟事件 |
| 缺失或无效 | 有效 | 保存为结束时间的零分钟事件 |
| 均缺失或无效 | 任意 | 整批拒绝，不写入 |
| 有效 | 有效但 `end < start` | 整批拒绝，不猜测跨日 |

“无效”包括无法按约定格式解析、日期越界或时区转换失败。

不再使用导入当下作为历史事件时间兜底。导入当下只用于 `createdAt` 和 `updatedAt`。

### 8.3 内容校验

- `eventText.trim()` 为空：整批拒绝。
- 返回 0 条记录：视为解析失败，不显示“已入录 0 则”。
- 非法 `moodTag`：降级为 `null`，不拒绝整批。
- 空白 `moodNote`：保存为 `null`。
- `zoneId`：整批使用发起导入时的 `Clock.zone.id`。
- `eventDate`：逐条由候选事件的最终 `startedAt` 和该 `zoneId` 计算。

### 8.4 重复导入

再次导入规范化后完全相同的原文时，唯一索引会阻止写入。Repository 应把唯一约束异常转换为用户可理解的结果，例如：

```text
这份记录已经导入过，未重复保存
```

同一批次必须保持原子性：不能因前几条先插入而留下半批数据。继续使用 Room 的列表插入事务和默认 `ABORT` 冲突策略。

## 9. 数据库迁移 `3 -> 4`

### 9.1 原则

- 数据库版本升级为 4。
- 保留 `MIGRATION_1_2` 和 `MIGRATION_2_3`。
- 新增 `MIGRATION_3_4`，不得使用破坏性迁移。
- 迁移只重建 `note_records`，不改动省察相关表。
- 重建完成后创建三个新索引。
- schema 继续导出到 `app/schemas`。

### 9.2 历史时区不可恢复的处理

版本 1、2、3 没有保存原始时区，因此无法完美还原旧事件的原始日期。迁移采用一次性、可解释的策略：

- 在数据库打开时捕获设备当前 `ZoneId`，称为 `migrationZone`。
- 所有旧事件的 `zoneId` 写为 `migrationZone.id`。
- 每条旧事件的 `eventDate` 使用 `startedAt` 在 `migrationZone` 中计算。
- 旧事件来源写为 `manual`。
- `importBatchId`、`importOrdinal` 写为 `null`，因为无法可靠区分旧手动记录和旧导入记录。

迁移后日期固定，不再随时区变化。此策略只能保证从迁移时开始稳定，不能推断历史真实所在地；这是旧 schema 信息缺失造成的不可避免限制。

### 9.3 可测试的迁移工厂

不要把 `ZoneId.systemDefault()` 隐藏在迁移 SQL 中。建议提供：

```kotlin
internal fun migration3To4(zoneId: ZoneId): Migration
```

生产数据库构建时传入 `ZoneId.systemDefault()`；迁移测试传入固定 `UTC` 或 `Asia/Shanghai`。这样事件日期回填可以确定性断言。

### 9.4 重建步骤

迁移按以下顺序执行：

1. 创建具有版本 4 完整列定义的 `note_records_new`。
2. 查询旧表所有记录。
3. 使用固定 `migrationZone` 在 Kotlin 中计算每条 `eventDate`。
4. 把原字段和新字段写入新表，保留原 `id`。
5. 校验新表行数与旧表一致。
6. 删除旧 `note_records`。
7. 将新表重命名为 `note_records`。
8. 创建三个索引。

不要依赖 SQLite `localtime` 修饰符回填日期，因为测试进程、设备系统和 Java `ZoneId` 的时区来源难以固定一致。

## 10. 直接影响的文件

### 数据层

- Modify `app/src/main/java/com/example/royalnote/data/NoteRecord.kt`
- Modify `app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt`
- Modify `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`
- Modify `app/src/main/java/com/example/royalnote/data/RoyalNoteDatabase.kt`
- Modify `app/schemas/com.example.royalnote.data.RoyalNoteDatabase/4.json`

### 手动记录链路

- Modify `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`
- Modify `app/src/main/java/com/example/royalnote/ui/TimeRangeFormatting.kt`
- Modify `app/src/main/java/com/example/royalnote/ui/TimeRangeFields.kt`
- Modify `app/src/main/java/com/example/royalnote/MainActivity.kt`
- Modify `app/src/main/java/com/example/royalnote/SeedData.kt`

### 导入链路

- Modify `app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt`
- Modify `app/src/main/java/com/example/royalnote/network/OpenRouterService.kt` only if prompt wording must clarify missing times; response schema remains unchanged

### 七日省察读取链路

- Modify `app/src/main/java/com/example/royalnote/reflection/ReflectionRepository.kt`
- Modify `app/src/main/java/com/example/royalnote/ui/ReflectionViewModel.kt` only where it builds or passes the record date window

### 测试

- Modify `app/src/test/java/com/example/royalnote/data/NoteRepositoryTest.kt`
- Create `app/src/androidTest/java/com/example/royalnote/data/NoteRecordDaoTest.kt`
- Modify `app/src/androidTest/java/com/example/royalnote/data/RoyalNoteDatabaseMigrationTest.kt`
- Modify `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`
- Modify `app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt`
- Modify `app/src/test/java/com/example/royalnote/ui/TimeRangeFormattingTest.kt`
- Modify affected reflection tests and `NoteRecord` fixtures

## 11. 实施任务

### Task 1：锁定日期与来源领域模型

- [x] 为 `NoteRecord` 增加 `eventDate`、`zoneId`、`source`、`importBatchId`、`importOrdinal`。
- [x] 增加记录来源常量或 enum 到数据库字符串的集中映射。
- [x] 为 Repository 增加规范化与不变量测试，先确认新测试失败。
- [x] 覆盖空正文、非法心情、倒置区间、非法时区和来源组合。
- [x] 保证 UI 和导入不直接计算或信任 `eventDate`。

完成条件：所有写入路径只能通过同一套规范化逻辑生成合法实体。

### Task 2：实现版本 4 migration

- [x] 把 `@Database` 版本改为 4。
- [x] 实现可注入 `ZoneId` 的 `migration3To4`。
- [x] 重建事件表并保留全部原字段和主键。
- [x] 创建日期、时间和导入唯一索引。
- [x] 导出并审查 `4.json`。
- [x] 添加 `1 -> 4`、`2 -> 4`、`3 -> 4` 仪器测试。

完成条件：每条旧事件只出现一次，正文、心情、起止时间、创建时间、修改时间和主键均保持不变；新日期字段按固定测试时区正确回填。

### Task 3：切换稳定日期查询

- [x] 为完整时间线补充 `id` 最终排序。
- [x] 新增 `recordsInDateRange`。
- [x] 七日省察改传 ISO 日期边界，不再传当前时区换算后的毫秒边界。
- [x] 时间线按 `eventDate` 分组。
- [x] 卡片和编辑器使用记录自身 `zoneId` 格式化时间。
- [x] 增加更换设备时区后分组不变的测试。

完成条件：同一数据库在两个不同时区打开时，每条记录仍属于同一个 ISO 日期分组，七日查询结果不漂移。

### Task 4：统一手动新增、编辑和删除

- [x] 新增时由 Repository 生成日期、来源和元数据。
- [x] 编辑时保留 `createdAt`、`zoneId` 和来源字段，并重算 `eventDate`。
- [x] 更新和删除检查 DAO 受影响行数。
- [x] 保留现有保存中防重复点击和失败保留表单行为。
- [x] 更新 SeedData 和全部测试 fixture。

完成条件：手动新增、编辑、删除继续通过现有行为测试，并新增存储不变量断言。

### Task 5：修正导入时间和幂等性

- [x] 实现规范化原文与 SHA-256 `importBatchId`。
- [x] 为每条导入候选分配稳定 `importOrdinal`。
- [x] 按时间纠错矩阵处理单边有效、双边无效和倒置区间。
- [x] 空列表、空正文或不可恢复时间导致整批拒绝。
- [x] 重复批次转换为明确提示，不显示通用保存失败。
- [x] 验证列表插入保持事务原子性。

完成条件：无效历史时间不会落到今天；同一原文连续导入两次时数据库只保留第一批。

### Task 6：补齐真实数据库测试

- [x] 使用 in-memory Room 或测试数据库验证新增、范围查询、排序和删除。
- [x] 验证同 `startedAt`、同 `createdAt` 时按 `id` 稳定排序。
- [x] 验证 ISO 日期范围的起始包含、结束排除边界。
- [x] 验证唯一导入索引允许多个手动 `NULL`，拒绝重复批次序号。
- [x] 用 `EXPLAIN QUERY PLAN` 或索引元数据确认关键查询使用目标索引。
- [x] 在连接设备或模拟器上运行所有 migration 测试。

完成条件：测试不只验证 fake DAO，还能证明实际 Room schema 和 SQLite 行为符合约定。

## 12. 测试矩阵

### 12.1 Repository/JVM

- 新增会 trim 正文和心情补述。
- 空白正文不会调用 DAO。
- 非法心情不会写入。
- `endedAt < startedAt` 被拒绝；相等被接受。
- `eventDate` 在指定 `zoneId` 下计算正确。
- 编辑保留 `createdAt` 和来源，更新 `updatedAt`。
- 编辑时间后重新计算 `eventDate`。
- DAO 更新或删除 0 行时返回失败。

### 12.2 导入/JVM

- 两端有效时保存完整范围。
- 只有开始有效时保存开始点。
- 只有结束有效时保存结束点。
- 两端无效时整批不写入。
- 倒置区间整批不写入。
- 解析结果为空时不写入。
- 空白事件正文导致整批不写入。
- 非法心情降级为 `null`。
- 相同规范化原文产生相同 `importBatchId`。
- 导入元数据时间与事件发生时间分离。
- 重复导入显示专用提示。

### 12.3 DAO/Android

- 完整时间线倒序稳定。
- 日期范围查询升序稳定。
- 范围开始日包含，结束日排除。
- 跨日事件只按 `eventDate` 返回一次。
- 多个手动记录的空导入字段不触发唯一冲突。
- 相同 `importBatchId + importOrdinal` 触发冲突且整批回滚。
- 更新和删除返回正确行数。

### 12.4 Migration/Android

- `1 -> 4`：旧 `createdAt` 同时初始化起止时间，再回填稳定日期。
- `2 -> 4`：保留既有起止时间。
- `3 -> 4`：保留事件表及全部省察表。
- 固定 UTC 和非 UTC 时区下回填日期正确。
- 自增主键序列在迁移后继续增长，不覆盖旧 id。
- 三个版本 4 索引均存在。
- Room 最终 schema validation 通过。

### 12.5 时区与夏令时

- 在 `America/Los_Angeles` 保存午夜附近事件后，以 `Asia/Shanghai` 展示，`eventDate` 不变。
- 夏令时跳时区间由现有 Java time 规则归一化，保存的 `zoneId` 和日期一致。
- 夏令时重叠时沿用 `withEarlierOffsetAtOverlap()`，并添加回归测试。

## 13. 验收标准

全部满足才可认为改进完成：

- [x] 新安装和 `1/2/3 -> 4` 升级都不会清除事件。
- [x] 每条事件都有合法 `eventDate`、`zoneId` 和 `source`。
- [x] 改变设备时区不会改变事件所属日期或原始钟表时间。
- [x] 时间线和七日省察使用稳定日期语义。
- [x] 同时间事件排序可重复、可测试。
- [x] 空正文、非法区间和不可恢复导入时间不会进入数据库。
- [x] 同一份原文重复导入不会产生重复记录。
- [x] 手动新增、编辑、删除、跨日和零分钟行为不回退。
- [x] 版本 4 schema 已导出并纳入版本控制。
- [x] JVM、lint、debug build 和连接设备上的事件存储迁移测试全部通过；完整 UI 套件仍有 2 条与存储无关的设备尺寸/列表可见性用例失败。

## 14. 验证命令

macOS/Linux：

```bash
bash ./gradlew app:testDebugUnitTest
bash ./gradlew lintDebug
bash ./gradlew assembleDebug
bash ./gradlew connectedDebugAndroidTest
```

若本机需要显式环境变量：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
bash ./gradlew app:testDebugUnitTest
```

Windows：

```powershell
.\gradlew.bat app:testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

## 15. 实施顺序与提交边界

推荐按以下顺序实施，避免在迁移完成前产生无法打开的中间 schema：

1. 先写领域约束、迁移和 DAO 失败测试。
2. 一次性完成实体、数据库版本、迁移和 schema 导出。
3. 切换 Repository 写入和 DAO 查询。
4. 更新手动记录、导入和省察调用方。
5. 更新 fixtures，运行全量 JVM 测试。
6. 运行 lint 和 debug build。
7. 在设备或模拟器上执行迁移测试和手动时区检查。

实现期间不得使用 `fallbackToDestructiveMigration`。如果迁移测试失败，应修正迁移或 schema，不得通过清库掩盖问题。

## 16. 风险与回退

### 16.1 主要风险

- 旧记录没有原始时区，只能按迁移时区固定日期。
- `NoteRecord` 新增必填字段会影响大量测试 fixture。
- 七日省察从毫秒范围切换到日期范围后，边界测试必须同步更新。
- 导入唯一键策略会把完全相同的原文视为同一批次，这是有意行为。

### 16.2 发布前回退

若版本 4 尚未发布，可以撤回代码和 schema 变更，继续使用版本 3。

### 16.3 发布后回退

版本 4 一旦产生用户数据库，不得简单降回版本 3。后续修正必须通过版本 5 migration 前进处理。发布前必须完成真实设备迁移验证并保留版本 4 schema JSON。

## 17. 完成定义

这项工作完成后，`note_records` 仍是一张简洁的事件事实表，但能够回答并稳定保持四个核心问题：

1. 发生了什么？
2. 在什么绝对时间发生？
3. 当时属于哪个自然日和时区？
4. 这条记录来自手动录入还是哪次导入？

这些信息足以支持现有时间线、编辑、导入和七日省察，同时避免为未确认功能提前扩展数据模型。
