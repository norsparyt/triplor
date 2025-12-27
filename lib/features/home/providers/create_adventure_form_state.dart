import 'package:triplor/features/home/domain/models/adventure_model.dart';

class CreateAdventureFormState {
  final String location;
  final DateTime? startDate;
  final DateTime? endDate;
  final Set<AdventureType> selectedTripStyles; // Multiple selection
  final bool isGroupTrip; // true for Group Trip, false for Travel Buddy
  final int maxPeople;
  final String description;

  CreateAdventureFormState({
    this.location = '',
    this.startDate,
    this.endDate,
    Set<AdventureType>?
    selectedTripStyles, //since set is mutable (in dart default parameters must be compile time constants)
    this.isGroupTrip = false,
    this.maxPeople = 5,
    this.description = '',
  }) : selectedTripStyles =
           selectedTripStyles ??
           {}; //assign the new set if null values is passed to constructor

  CreateAdventureFormState copyWith({
    String? location,
    DateTime? startDate,
    DateTime? endDate,
    Set<AdventureType>? selectedTripStyles,
    bool? isGroupTrip,
    int? maxPeople,
    String? description,
  }) {
    return CreateAdventureFormState(
      location: location ?? this.location,
      startDate: startDate ?? this.startDate,
      endDate: endDate ?? this.endDate,
      selectedTripStyles: selectedTripStyles ?? this.selectedTripStyles,
      isGroupTrip: isGroupTrip ?? this.isGroupTrip,
      maxPeople: maxPeople ?? this.maxPeople,
      description: description ?? this.description,
    );
  }

  //TODO: More validations??
  bool get isValid {
    return location.isNotEmpty &&
        startDate != null &&
        endDate != null &&
        selectedTripStyles.isNotEmpty &&
        description.isNotEmpty;
  }
}
