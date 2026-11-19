const palette: Record<string, string> = {
  '1': '#7986CB', '2': '#33B679', '3': '#8E24AA', '4': '#E67C73', '5': '#F6BF26', '6': '#F4511E',
  '7': '#039BE5', '8': '#616161', '9': '#3F51B5', '10': '#0B8043', '11': '#D50000',
}
export const eventColorHex = (colorId?: string | null) => (colorId ? palette[colorId] : undefined)

export type RepeatRule = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'
export const repeatRules: RepeatRule[] = ['NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY']
export const toRecurrence = (r: RepeatRule): string[] | undefined => (r === 'NONE' ? undefined : [`RRULE:FREQ=${r}`])
