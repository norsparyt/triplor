
import '../../features/home/domain/models/adventure_model.dart';
import 'location_model.dart';

class UserProfileModel {
  final String id;
  final String name;
  final int age;
  final String avatarUrl;
  final LocationModel currentLocation;
  final List<AdventureModel> adventures;

  const UserProfileModel({
    required this.id,
    required this.name,
    required this.age,
    required this.avatarUrl,
    required this.currentLocation,
    this.adventures = const [],
  });
}