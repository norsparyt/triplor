class CreateAdventureFormState {
  final String location;
  final DateTime? startDate;
  final DateTime? endDate;
  final Set<String> selectedTripStyles; // Multiple selection
  final bool isGroupTrip; // true for Group Trip, false for Travel Buddy
  final int maxPeople;
  final String description;

  CreateAdventureFormState({
    this.location = '',
    this.startDate,
    this.endDate,
    Set<String>? selectedTripStyles,
    this.isGroupTrip = true,
    this.maxPeople = 5,
    this.description = '',
  }) : selectedTripStyles = selectedTripStyles ?? {};

  CreateAdventureFormState copyWith({
    String? location,
    DateTime? startDate,
    DateTime? endDate,
    Set<String>? selectedTripStyles,
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
}
