class DeleteAdventureState {
  final bool isLoading;
  final String? error;
  final bool isDeleted;

  DeleteAdventureState({
    this.isLoading = false,
    this.error,
    this.isDeleted = false,
  });

  DeleteAdventureState copyWith({
    bool? isLoading,
    String? error,
    bool? isDeleted,
  }) {
    return DeleteAdventureState(
      isLoading: isLoading ?? this.isLoading,
      error: error,
      isDeleted: isDeleted ?? this.isDeleted,
    );
  }
}
