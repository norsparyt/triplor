enum MatchRequestStatus {
  pending,
  accepted,
  rejected,
}

class MatchRequestModel {
  final String id;
  final String fromUserId;
  final String toUserId;
  final MatchRequestStatus status;
  final DateTime createdAt;

  const MatchRequestModel({
    required this.id,
    required this.fromUserId,
    required this.toUserId,
    required this.status,
    required this.createdAt,
  });
}