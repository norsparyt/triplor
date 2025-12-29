import 'package:triplor/features/home/domain/models/adventure_model.dart';

class CreateAdventureFormState {
  final String location;
  final DateTime? startDate;
  final DateTime? endDate;
  final Set<AdventureStyle> selectedAdventureStyles; // Multiple selection
  final bool
  isGroupAdventure; // true for Group Adventure, false for Travel Buddy
  final int maxPeople;
  final String description;

  CreateAdventureFormState({
    this.location = '',
    this.startDate,
    this.endDate,
    Set<AdventureStyle>?
    selectedAdventureStyles, //since set is mutable (in dart default parameters must be compile time constants)
    this.isGroupAdventure = false, //default solo trip= matches UI
    this.maxPeople = 1, //default solo trip
    this.description = '',
  }) : selectedAdventureStyles =
           selectedAdventureStyles ??
           {}; //assign the new set if null values is passed to constructor

  CreateAdventureFormState copyWith({
    String? location,
    DateTime? startDate,
    DateTime? endDate,
    Set<AdventureStyle>? selectedAdventureStyles,
    bool? isGroupAdventure,
    int? maxPeople,
    String? description,
  }) {
    return CreateAdventureFormState(
      location: location ?? this.location,
      startDate: startDate ?? this.startDate,
      endDate: endDate ?? this.endDate,
      selectedAdventureStyles: selectedAdventureStyles != null
          ? Set.of(selectedAdventureStyles)
          : Set.of(this.selectedAdventureStyles),
      isGroupAdventure: isGroupAdventure ?? this.isGroupAdventure,
      maxPeople: maxPeople ?? this.maxPeople,
      description: description ?? this.description,
    );
  }

  //TODO: More validations??
  bool get isValid {
    return location.isNotEmpty &&
        startDate != null &&
        endDate != null &&
        selectedAdventureStyles.isNotEmpty &&
        description.isNotEmpty;
  }
}
