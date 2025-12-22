class ChatMessageModel {
  final String id;
  final String senderId;
  final String message;
  final DateTime sentAt;

  const ChatMessageModel({
    required this.id,
    required this.senderId,
    required this.message,
    required this.sentAt,
  });
}