# SQLite 聊天数据库实现

## 概述
此项目已从 SharedPreferences 存储迁移到 SQLite 数据库，以提供更可靠和可扩展的聊天数据存储。

## 文件结构

### 数据库相关文件
1. **DatabaseHelper.java** (`data/sqlite/DatabaseHelper.java`)
   - SQLite 数据库帮助类
   - 创建和管理 messages 和 conversations 表
   - 提供所有数据库操作方法

2. **ChatStorageHelper.java** (`Chat/utils/ChatStorageHelper.java`)
   - 更新以使用 SQLite 而非 SharedPreferences
   - 提供高级数据访问接口

3. **DatabaseManager.java** (`Chat/utils/DatabaseManager.java`)
   - 单例模式的数据库管理器
   - 提供便捷的数据库操作方法

4. **DatabaseInitializer.java** (`Chat/utils/DatabaseInitializer.java`)
   - 数据库初始化工具
   - 提供示例数据

### 数据模型
- **Message.java** (`data/Message.java`)
  - 聊天消息模型
  - 包含内容、类型、发送者、接收者和时间戳

- **Conversation.java** (`data/Conversation.java`)
  - 会话模型
  - 包含两个用户和消息列表

- **User.java** (`data/User.java`)
  - 用户模型
  - 包含ID、用户名、电话等信息

## 数据库表结构

### messages 表
- id: 主键，自增
- content: 消息内容
- type: 消息类型（sent/received）
- sender_id: 发送者ID
- receiver_id: 接收者ID
- timestamp: 时间戳
- conversation_id: 会话ID

### conversations 表
- id: 主键，自增
- user_me_id: 当前用户ID
- user_opposite_id: 对方用户ID
- unread_count: 未读消息数
- latest_message: 最新消息内容
- latest_message_time: 最新消息时间戳

## 主要功能

### 1. 保存会话
```java
storageHelper.saveConversation(conversation);
```

### 2. 获取会话
```java
Conversation conv = storageHelper.getConversationByOppositeId(oppositeId, currentUserId);
```

### 3. 获取所有会话
```java
List<Conversation> conversations = storageHelper.loadConversations();
```

### 4. 删除会话
```java
storageHelper.deleteConversation(userId, oppositeId);
```

### 5. 发送消息
```java
Message message = new Message(content, type, senderId, receiverId);
storageHelper.saveConversation(conversation); // 会自动保存消息
```

## 初始化示例数据

在应用启动时，可以使用 DatabaseInitializer 来初始化示例数据：

```java
DatabaseInitializer initializer = new DatabaseInitializer(context);
if (!initializer.hasData(currentUserId)) {
    initializer.initializeSampleData(currentUserId);
}
```

## 注意事项

1. 数据库版本管理：当需要修改表结构时，需要更新 DATABASE_VERSION 并实现 onUpgrade 方法
2. 线程安全：所有数据库操作都应该在后台线程执行
3. 数据备份：SQLite 数据库文件默认存储在应用的私有目录中
4. 性能优化：对于大量消息，考虑实现分页加载机制

## 迁移说明

从 SharedPreferences 迁移到 SQLite 的主要优势：
- 更好的性能，特别是大量数据时
- 支持复杂查询和关联操作
- 数据一致性更好
- 支持事务操作
- 更容易实现数据同步功能